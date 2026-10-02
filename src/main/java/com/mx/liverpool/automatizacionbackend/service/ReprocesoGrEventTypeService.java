package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReprocesoGrEventTypeService {
    private static final String CAMPO = "ExtnGREventType";
    private static final int LONGITUD_MAXIMA = 24;
    private static final String MARCA_NO_APLICA = "No " + CAMPO + " > " + LONGITUD_MAXIMA;

    private final ReprocesoService reprocesoService;
    private final ObjectMapper objectMapper;

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar grEventType con {} filas", filas.size());
        List<ReprocesoResult> resultados = reprocesoService.reprocesar(filas, ReprocesoNodeRow::getTrackingNumber, this::preparar);
        log.info("Finalizando reprocesar grEventType");
        return resultados;
    }

    private ReprocesoService.Preparado preparar(ReprocesoNodeRow fila) {
        try {
            JsonNode raiz = objectMapper.readTree(fila.getJson());
            if (!recortar(raiz)) {
                log.info("Fila con tracking {} no contiene {} de más de {} caracteres, se omite el envío",
                        fila.getTrackingNumber(), CAMPO, LONGITUD_MAXIMA);
                return ReprocesoService.Preparado.omitir(fila.getJson(), MARCA_NO_APLICA);
            }
            return ReprocesoService.Preparado.enviar(objectMapper.writeValueAsString(raiz));
        } catch (Exception e) {
            log.error("Error procesando el JSON del tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }
    }

    /**
     * Recorta a 24 caracteres toda llave ExtnGREventType que los exceda, a cualquier profundidad; devuelve
     * si cambió alguna. El INT200 declara 40 pero YFS_ORDER_HEADER.EXTN_GR_EVENT_TYPE solo acepta 24.
     */
    static boolean recortar(JsonNode orden) {
        boolean recortado = false;
        for (JsonNode padre : orden.findParents(CAMPO)) {
            String valor = padre.get(CAMPO).asText("");
            if (valor.length() > LONGITUD_MAXIMA && padre instanceof ObjectNode nodo) {
                String limpio = valor.strip();
                // ponytail: cuenta caracteres; si la columna de OMS es VARCHAR2(24 BYTE) recortar por bytes UTF-8.
                nodo.put(CAMPO, limpio.substring(0, Math.min(limpio.length(), LONGITUD_MAXIMA)).stripTrailing());
                recortado = true;
            }
        }
        return recortado;
    }
}
