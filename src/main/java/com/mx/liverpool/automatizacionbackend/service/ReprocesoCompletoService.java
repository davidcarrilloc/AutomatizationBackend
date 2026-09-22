package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.liverpool.automatizacionbackend.model.ClienteRemision;
import com.mx.liverpool.automatizacionbackend.model.DetalleSkuRemision;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import com.mx.liverpool.automatizacionbackend.repository.RemisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReprocesoCompletoService {
    private static final String MARCA_ENVIADO = "Enviado";
    private static final String MARCA_F001 = "F001";
    private static final String MARCA_ITEM = "Item";
    private static final String MARCA_CORREO = "Correo";
    private static final String SIN_CAMBIOS = "sin cambios";
    private static final String NODO_ORDER_LINES = "OrderLines";

    private final ReprocesoService reprocesoService;
    private final ReprocesoBillToService reprocesoBillToService;
    private final RemisionRepository remisionRepository;
    private final ObjectMapper objectMapper;

    /** Una fila transformada: si debe enviarse (Preparado) y la nota de correcciones aplicadas. */
    public record FilaPreparada(ReprocesoNodeRow fila, ReprocesoService.Preparado preparado, String nota) {
    }

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar completo con {} filas", filas.size());

        List<FilaPreparada> preparadas = prepararFilas(filas);
        List<ReprocesoResult> crudos = reprocesoService.reprocesar(preparadas,
                fp -> fp.fila().getTrackingNumber(), FilaPreparada::preparado);

        // Los resultados vuelven en el orden de las filas: se zipean por índice para marcar "Enviado".
        List<ReprocesoResult> resultados = new ArrayList<>();
        for (int i = 0; i < crudos.size(); i++) {
            resultados.add(aplicarMarca(preparadas.get(i).nota(), crudos.get(i)));
        }

        log.info("Finalizando reprocesar completo con {} resultados", resultados.size());
        return resultados;
    }

    /**
     * Batch de BRIDGECORE (una consulta por remisión distinta) + transformación fila por fila, sin enviar.
     * Lo comparten el reproceso síncrono y el asíncrono: el envío lo decide quien llame.
     */
    public List<FilaPreparada> prepararFilas(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a prepararFilas completo con {} filas", filas.size());

        // Dos consultas por remisión distinta, no por fila. Van separadas a propósito: una remisión
        // sin partidas no devuelve detalle de SKU y aun así tiene cliente.
        Map<String, List<DetalleSkuRemision>> detallePorRemision = new HashMap<>();
        Map<String, ClienteRemision> clientePorRemision = new HashMap<>();
        List<String> remisiones = filas.stream().map(this::remision).filter(r -> !r.isEmpty()).distinct().toList();
        for (String remision : remisiones) {
            List<DetalleSkuRemision> detalle = remisionRepository.obtenerDetalleSkuRemision(remision);
            List<ClienteRemision> clientes = remisionRepository.obtenerClienteRemision(remision);
            log.info("BRIDGECORE devolvió {} SKUs y {} clientes para la remisión {}", detalle.size(), clientes.size(), remision);
            detallePorRemision.put(remision, detalle);
            if (!clientes.isEmpty()) clientePorRemision.put(remision, clientes.getFirst());
        }

        // Identidad y no igualdad: dos filas con el mismo JSON y tracking son objetos distintos.
        Map<ReprocesoNodeRow, String> notas = new IdentityHashMap<>();
        List<FilaPreparada> preparadas = new ArrayList<>();
        for (ReprocesoNodeRow fila : filas) {
            ReprocesoService.Preparado preparado = preparar(fila, detallePorRemision, clientePorRemision, notas);
            preparadas.add(new FilaPreparada(fila, preparado, notas.get(fila)));
        }

        log.info("Finalizando prepararFilas completo con {} filas preparadas", preparadas.size());
        return preparadas;
    }

    /** Marca "Enviado (nota) | response" en las filas que sí se enviaron; un response con error se conserva tal cual. */
    public ReprocesoResult aplicarMarca(String nota, ReprocesoResult crudo) {
        return nota == null || crudo.getResponse().startsWith("\"error\"")
                ? crudo
                : ReprocesoResult.builder()
                        .requestOriginal(crudo.getRequestOriginal())
                        .trackingNumber(crudo.getTrackingNumber())
                        .response(MARCA_ENVIADO + " (" + nota + ") | " + crudo.getResponse())
                        .build();
    }

    private ReprocesoService.Preparado preparar(ReprocesoNodeRow fila,
                                                Map<String, List<DetalleSkuRemision>> detallePorRemision,
                                                Map<String, ClienteRemision> clientePorRemision,
                                                Map<ReprocesoNodeRow, String> notas) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(fila.getJson());
        } catch (Exception e) {
            log.error("Error procesando el JSON del tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }

        // ATG envía el pedido sin llave envolvente y la fachada sí la manda: se toleran ambas entradas.
        JsonNode orden = raiz.has("Order") ? raiz.get("Order") : raiz;
        String remision = remision(fila);
        List<String> aplicadas = new ArrayList<>();
        List<String> saltadas = new ArrayList<>();

        // 1. F001 -> 001. Nunca frena la orden: que no traiga F001 no es un defecto.
        if (ReprocesoF001Service.corregirStore(orden)) aplicadas.add(MARCA_F001);

        // 2. Item y precios. Sin asignación posicional confiable se salta y la orden sigue su camino.
        corregirItem(orden, detallePorRemision.getOrDefault(remision, List.of()), aplicadas, saltadas);

        // 3. Correo y nombre. Va antes que el BillTo para que el merge herede lo que se escribió en
        // el ShipTo en vez de reclamarlo vacío.
        ClienteRemision cliente = clientePorRemision.getOrDefault(remision, new ClienteRemision(null, null));
        String antesDelCliente = orden.toString();
        List<String> faltantesCliente = ReprocesoItemIdAutoService
                .rellenarCampos(orden, ReprocesoFirstNameService.valores(cliente))
                .stream().distinct().sorted().toList();
        if (!orden.toString().equals(antesDelCliente)) aplicadas.add(MARCA_CORREO);

        // 4. BillTo desde ShipTo + defaults del INT200.
        List<String> faltantesBillTo = reprocesoBillToService.completarBillTo(orden, aplicadas);

        // El veto mira el resultado, no la fuente: solo frena lo que el INT200 exige y quedó vacío.
        List<String> motivos = new ArrayList<>();
        if (!faltantesCliente.isEmpty()) motivos.add("Sin datos en BRIDGECORE para: " + String.join(", ", faltantesCliente));
        if (!faltantesBillTo.isEmpty()) motivos.add("Falta: " + String.join(", ", faltantesBillTo));
        if (!motivos.isEmpty()) {
            log.info("Fila con tracking {} incompleta, se omite el envío: {}", fila.getTrackingNumber(), motivos);
            return ReprocesoService.Preparado.omitir(fila.getJson(), "No enviado. " + String.join(". ", motivos));
        }

        try {
            String nota = aplicadas.isEmpty() ? SIN_CAMBIOS : String.join(", ", aplicadas);
            notas.put(fila, saltadas.isEmpty() ? nota : nota + " | " + String.join(", ", saltadas));
            return ReprocesoService.Preparado.enviar(objectMapper.writeValueAsString(raiz));
        } catch (Exception e) {
            log.error("Error serializando el JSON del tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            notas.remove(fila);
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"" + e.getMessage() + "\"");
        }
    }

    /** Corrige Item y precios solo si BRIDGECORE trae el detalle y cuadra con los OrderLine del JSON. */
    private void corregirItem(JsonNode orden, List<DetalleSkuRemision> skus, List<String> aplicadas, List<String> saltadas) {
        if (skus.isEmpty()) {
            saltadas.add("sin Item: BC no trae detalle de SKU");
            return;
        }
        List<JsonNode> orderLines = new ArrayList<>();
        orden.path(NODO_ORDER_LINES).forEach(orderLines::add);
        if (orderLines.size() != skus.size()) {
            saltadas.add("sin Item: BC trae " + skus.size() + " SKUs y el JSON " + orderLines.size() + " OrderLines");
            return;
        }
        String antes = orden.path(NODO_ORDER_LINES).toString();
        ReprocesoItemIdAutoService.llenarItem(orderLines, skus, true);
        if (!orden.path(NODO_ORDER_LINES).toString().equals(antes)) aplicadas.add(MARCA_ITEM);
    }

    private String remision(ReprocesoNodeRow fila) {
        return Objects.toString(fila.getTrackingNumber(), "").trim();
    }
}
