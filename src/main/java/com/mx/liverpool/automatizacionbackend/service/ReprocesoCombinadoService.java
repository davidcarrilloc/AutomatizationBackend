package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReprocesoCombinadoService {
    private static final String STORE_ORIGEN = "F001";
    private static final String NODO_ORIGEN = "PersonInfoShipTo";
    private static final String MARCA_ENVIADO = "Enviado";
    private static final String SIN_CAMBIOS = "sin cambios";
    private static final String SIN_ORIGEN = "Falta el nodo " + NODO_ORIGEN + ", no hay de donde copiar";

    private final ReprocesoService reprocesoService;
    private final ReprocesoBillToService reprocesoBillToService;
    private final ObjectMapper objectMapper;

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar combinado con {} filas", filas.size());

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
                            .response(MARCA_ENVIADO + " (" + nota + ") | " + crudo.getResponse())
                            .build());
        }

        log.info("Finalizando reprocesar combinado con {} resultados", resultados.size());
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
        List<String> aplicadas = new ArrayList<>();

        if (ReprocesoF001Service.corregirStore(orden)) aplicadas.add(STORE_ORIGEN);

        if (!(orden instanceof ObjectNode) || !(orden.get(NODO_ORIGEN) instanceof ObjectNode shipTo)
                || ReprocesoBillToService.sinDatos(shipTo)) {
            log.info("Fila con tracking {} sin {}, se omite el envío", fila.getTrackingNumber(), NODO_ORIGEN);
            return ReprocesoService.Preparado.omitir(fila.getJson(), SIN_ORIGEN);
        }

        List<String> faltantes = reprocesoBillToService.completarBillTo(orden, aplicadas);

        if (!faltantes.isEmpty()) {
            log.info("Fila con tracking {} incompleta, se omite el envío: {}", fila.getTrackingNumber(), faltantes);
            return ReprocesoService.Preparado.omitir(fila.getJson(), "Falta: " + String.join(", ", faltantes));
        }

        try {
            notas.put(fila, aplicadas.isEmpty() ? SIN_CAMBIOS : String.join(", ", aplicadas));
            return ReprocesoService.Preparado.enviar(objectMapper.writeValueAsString(raiz));
        } catch (Exception e) {
            log.error("Error serializando el JSON del tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            notas.remove(fila);
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"" + e.getMessage() + "\"");
        }
    }

}
