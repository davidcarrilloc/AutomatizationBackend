package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoFacadeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReprocesoFacadeService {
    private final ReprocesoService reprocesoService;
    private final ObjectMapper objectMapper;

    public List<ReprocesoResult> reprocesar(List<ReprocesoFacadeRow> filas) {
        return reprocesoService.reprocesar(filas, ReprocesoFacadeRow::getRemision, this::preparar);
    }

    private ReprocesoService.Preparado preparar(ReprocesoFacadeRow fila) {
        try {
            return ReprocesoService.Preparado.enviar(construirBody(fila.getJson(), fila.getItemId()));
        } catch (Exception e) {
            log.error("Error construyendo el body para la remisión {}: {}", fila.getRemision(), e.getMessage());
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }
    }

    private String construirBody(String json, String itemId) throws Exception {
        JsonNode raiz = objectMapper.readTree(json);
        JsonNode orderLines = raiz.path("OrderLines");
        for (JsonNode orderLine : orderLines) {
            JsonNode item = orderLine.path("OrderLine").path("Item");
            if (item instanceof ObjectNode itemNode) {
                itemNode.set("ItemID", nodoItemId(itemId));
            }
        }
        return objectMapper.writeValueAsString(raiz);
    }

    private JsonNode nodoItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return TextNode.valueOf("");
        String limpio = itemId.trim();
        return limpio.matches("\\d+")
                ? LongNode.valueOf(Long.parseLong(limpio))
                : TextNode.valueOf(limpio);
    }
}
