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
public class ReprocesoF001Service {
    private static final String STORE_ORIGEN = "F001";
    private static final String STORE_DESTINO = "001";
    private static final String MARCA_NO_APLICA = "No F001";
    private static final List<String> CAMPOS_NODO = List.of("Store", "ShipNode");

    private final ReprocesoService reprocesoService;
    private final ObjectMapper objectMapper;

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        return reprocesoService.reprocesar(filas, ReprocesoNodeRow::getTrackingNumber, this::preparar);
    }

    private ReprocesoService.Preparado preparar(ReprocesoNodeRow fila) {
        try {
            JsonNode raiz = objectMapper.readTree(fila.getJson());
            if (!corregirStore(raiz)) {
                log.info("Fila con tracking {} no contiene Store ni ShipNode {}, se omite el envío", fila.getTrackingNumber(), STORE_ORIGEN);
                return ReprocesoService.Preparado.omitir(fila.getJson(), MARCA_NO_APLICA);
            }
            return ReprocesoService.Preparado.enviar(objectMapper.writeValueAsString(raiz));
        } catch (Exception e) {
            log.error("Error procesando el JSON del tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }
    }

    /** Sustituye F001 por 001 en el Store y el ShipNode de cada OrderLine; devuelve si cambió alguno. */
    static boolean corregirStore(JsonNode orden) {
        boolean aplica = false;
        for (JsonNode orderLine : orden.path("OrderLines")) {
            if (orderLine.path("OrderLine") instanceof ObjectNode ordenNode) {
                for (String campo : CAMPOS_NODO) {
                    if (STORE_ORIGEN.equals(ordenNode.path(campo).asText())) {
                        ordenNode.put(campo, STORE_DESTINO);
                        aplica = true;
                    }
                }
            }
        }
        return aplica;
    }
}
