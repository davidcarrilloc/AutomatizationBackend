package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoFacadeResult;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoFacadeRow;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class ReprocesoFacadeService {
    private static final long PAUSA_ENTRE_ENVIOS_MS = 1000L;
    private static final long PAUSA_CADA_LOTE_MS = 4000L;
    private static final int TAMANO_LOTE = 10;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReprocesoFacadeService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl("https://apigee-pro.liverpool.com.mx")
                .defaultHeader("apikey", "wTFlLG22bZNEAQlKwbnk8KesSevGZASWi4XScdpYEmjG8Z0j")
                .build();
        this.objectMapper = objectMapper;
    }

    public List<ReprocesoFacadeResult> reprocesar(List<ReprocesoFacadeRow> filas) {
        log.info("Entrando a reprocesar con {} filas", filas.size());
        List<ReprocesoFacadeResult> resultados = new ArrayList<>();
        int contador = 0;
        for (ReprocesoFacadeRow fila : filas) {
            resultados.add(enviar(fila));
            contador++;
            if (contador < filas.size()) {
                pausar(contador % TAMANO_LOTE == 0 ? PAUSA_CADA_LOTE_MS : PAUSA_ENTRE_ENVIOS_MS);
            }
        }
        log.info("Finalizando reprocesar con {} resultados", resultados.size());
        return resultados;
    }

    private ReprocesoFacadeResult enviar(ReprocesoFacadeRow fila) {
        log.info("Enviando reproceso para remisión {}", fila.getRemision());
        String requestOriginal;
        try {
            requestOriginal = construirBody(fila.getJson(), fila.getItemId());
        } catch (Exception e) {
            log.error("Error construyendo el body para la remisión {}: {}", fila.getRemision(), e.getMessage());
            return ReprocesoFacadeResult.builder()
                    .requestOriginal(fila.getJson())
                    .trackingNumber(fila.getRemision())
                    .response("\"error\": \"JSON inválido: " + e.getMessage() + "\"")
                    .build();
        }

        try {
            String response = webClient.post()
                    .uri("/oms/sl/I200?origen=ecom")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestOriginal)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return construirResultado(fila, requestOriginal, response);
        } catch (WebClientResponseException e) {
            log.error("Error HTTP enviando remisión {}: {}", fila.getRemision(), e.getMessage());
            return construirResultado(fila, requestOriginal,
                    "\"error\": \"" + e.getMessage() + "\", \"body\": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error enviando remisión {}: {}", fila.getRemision(), e.getMessage());
            return construirResultado(fila, requestOriginal, "\"error\": \"" + e.getMessage() + "\"");
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

    private ReprocesoFacadeResult construirResultado(ReprocesoFacadeRow fila, String requestOriginal, String response) {
        return ReprocesoFacadeResult.builder()
                .requestOriginal(requestOriginal)
                .trackingNumber(fila.getRemision())
                .response(response == null ? "" : response)
                .build();
    }

    private void pausar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Pausa entre envíos interrumpida: {}", e.getMessage());
        }
    }
}
