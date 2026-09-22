package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mx.liverpool.automatizacionbackend.model.Int200Rules;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class ReprocesoBillToService {
    private static final String NODO_ORIGEN = "PersonInfoShipTo";
    private static final String NODO_DESTINO = "PersonInfoBillTo";
    private static final String PREFIJO_EXTENSION = "LExtn";
    private static final String PREFIJO_DEFAULT = "default ";
    static final String MARCA_BILLTO = "BillTo";
    private static final String MARCA_ENVIADO = "Enviado";
    private static final String SIN_ORIGEN = "Falta el nodo " + NODO_ORIGEN + ", no hay de donde copiar";

    private final ReprocesoService reprocesoService;
    private final ObjectMapper objectMapper;
    private final List<Int200Rules.Campo> obligatoriosBillTo;

    @Autowired
    public ReprocesoBillToService(ReprocesoService reprocesoService, ObjectMapper objectMapper,
                                  @Value("${int200.rules:classpath:int200-rules.json}") Resource recursoReglas) {
        this.reprocesoService = reprocesoService;
        this.objectMapper = objectMapper;
        try (InputStream is = recursoReglas.getInputStream()) {
            Int200Rules reglas = objectMapper.readValue(is, Int200Rules.class);
            this.obligatoriosBillTo = reglas.bloques().stream()
                    .filter(bloque -> NODO_DESTINO.equals(bloque.path()))
                    .findFirst()
                    .map(Int200Rules.Bloque::campos)
                    .orElseThrow(() -> new IllegalStateException("Las reglas del INT200 no traen el bloque " + NODO_DESTINO));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron cargar las reglas del INT200: " + e.getMessage());
        }
    }

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar billTo con {} filas", filas.size());

        // Identidad y no igualdad: dos filas con el mismo JSON y tracking son objetos distintos.
        Map<ReprocesoNodeRow, String> notas = new IdentityHashMap<>();
        List<ReprocesoResult> crudos = reprocesoService.reprocesar(
                filas, ReprocesoNodeRow::getTrackingNumber, fila -> preparar(fila, notas));

        // Los resultados vuelven en el orden de las filas: se zipean por índice para marcar "Enviado".
        List<ReprocesoResult> resultados = new ArrayList<>();
        for (int i = 0; i < crudos.size(); i++) {
            ReprocesoResult crudo = crudos.get(i);
            String nota = notas.get(filas.get(i));
            resultados.add(nota == null || crudo.getResponse().startsWith("\"error\"")
                    ? crudo
                    : ReprocesoResult.builder()
                            .requestOriginal(crudo.getRequestOriginal())
                            .trackingNumber(crudo.getTrackingNumber())
                            .response(MARCA_ENVIADO + nota + " | " + crudo.getResponse())
                            .build());
        }

        log.info("Finalizando reprocesar billTo con {} resultados", resultados.size());
        return resultados;
    }

    private ReprocesoService.Preparado preparar(ReprocesoNodeRow fila, Map<ReprocesoNodeRow, String> notas) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(fila.getJson());
        } catch (Exception e) {
            log.error("Error procesando el JSON del tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }

        // ATG envía el pedido sin llave envolvente y la fachada sí la manda: se toleran ambas entradas.
        JsonNode orden = raiz.has("Order") ? raiz.get("Order") : raiz;
        if (!(orden instanceof ObjectNode) || !(orden.get(NODO_ORIGEN) instanceof ObjectNode shipTo) || sinDatos(shipTo)) {
            log.info("Fila con tracking {} sin {}, se omite el envío", fila.getTrackingNumber(), NODO_ORIGEN);
            return ReprocesoService.Preparado.omitir(fila.getJson(), SIN_ORIGEN);
        }

        List<String> aplicadas = new ArrayList<>();
        List<String> faltantes = completarBillTo(orden, aplicadas);
        List<String> defaults = aplicadas.stream()
                .filter(aplicada -> aplicada.startsWith(PREFIJO_DEFAULT))
                .map(aplicada -> aplicada.substring(PREFIJO_DEFAULT.length()))
                .toList();

        if (!faltantes.isEmpty()) {
            log.info("Fila con tracking {} incompleta, se omite el envío: {}", fila.getTrackingNumber(), faltantes);
            return ReprocesoService.Preparado.omitir(fila.getJson(), "Falta: " + String.join(", ", faltantes));
        }

        try {
            notas.put(fila, defaults.isEmpty() ? "" : " (default " + String.join(", ", defaults) + ")");
            return ReprocesoService.Preparado.enviar(objectMapper.writeValueAsString(raiz));
        } catch (Exception e) {
            log.error("Error serializando el JSON del tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            notas.remove(fila);
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"" + e.getMessage() + "\"");
        }
    }

    /**
     * Rellena PersonInfoBillTo con lo que PersonInfoShipTo trae y aplica los defaults del INT200.
     * Anota en `aplicadas` lo que cambió ("BillTo" y cada default) y devuelve los obligatorios que
     * quedaron vacíos, que es lo que impide enviar la orden. Sin ShipTo utilizable no copia nada,
     * pero igual valida los obligatorios: un BillTo que ya venía completo no tiene por qué frenarse.
     */
    List<String> completarBillTo(JsonNode orden, List<String> aplicadas) {
        if (!(orden instanceof ObjectNode nodoOrden)) return List.of(NODO_DESTINO);

        ObjectNode billTo = orden.get(NODO_DESTINO) instanceof ObjectNode existente
                ? existente
                : nodoOrden.putObject(NODO_DESTINO);

        // Solo se rellena lo que BillTo trae vacío; lo que ya tiene valor propio se respeta.
        if (orden.get(NODO_ORIGEN) instanceof ObjectNode shipTo && !sinDatos(shipTo)) {
            List<Map.Entry<String, JsonNode>> copiables = shipTo.properties().stream()
                    .filter(campo -> !campo.getKey().startsWith(PREFIJO_EXTENSION))
                    .filter(campo -> !estaVacio(campo.getValue()))
                    .filter(campo -> estaVacio(billTo.get(campo.getKey())))
                    .toList();
            copiables.forEach(campo -> billTo.set(campo.getKey(), campo.getValue()));
            if (!copiables.isEmpty()) aplicadas.add(MARCA_BILLTO);
        }

        List<String> faltantes = new ArrayList<>();
        for (Int200Rules.Campo campo : obligatoriosBillTo) {
            if (!estaVacio(billTo.get(campo.nombre()))) continue;
            // Un obligatorio vacío con default en la definición no es un hueco: se rellena aquí.
            if (campo.valorDefault() == null) {
                faltantes.add(NODO_DESTINO + "." + campo.nombre());
            } else {
                billTo.put(campo.nombre(), campo.valorDefault());
                aplicadas.add(PREFIJO_DEFAULT + campo.nombre() + "=\"" + campo.valorDefault() + "\"");
            }
        }
        return faltantes;
    }

    static boolean sinDatos(ObjectNode nodo) {
        return nodo.properties().stream().allMatch(campo -> estaVacio(campo.getValue()));
    }

    static boolean estaVacio(JsonNode valor) {
        return valor == null || valor.isNull() || valor.asText().isBlank();
    }
}
