package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeResult;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
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
public class ReprocesoNodeService {
    private static final long PAUSA_ENTRE_ENVIOS_MS = 1000L;
    private static final long PAUSA_CADA_LOTE_MS = 4000L;
    private static final int TAMANO_LOTE = 10;
    private static final String STORE_ORIGEN = "F001";
    private static final String STORE_DESTINO = "001";
    private static final String MARCA_NO_APLICA = "No F001";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReprocesoNodeService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl("https://apigee-pro.liverpool.com.mx")
                .defaultHeader("apikey", "wTFlLG22bZNEAQlKwbnk8KesSevGZASWi4XScdpYEmjG8Z0j")
                .build();
        this.objectMapper = objectMapper;
    }

    public List<ReprocesoNodeResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar con {} filas", filas.size());
        List<ReprocesoNodeResult> resultados = new ArrayList<>();
        int enviados = 0;
        for (ReprocesoNodeRow fila : filas) {
            ResultadoCorreccion correccion;
            try {
                correccion = corregirStore(fila.getJson());
            } catch (Exception e) {
                log.error("Error procesando el JSON del tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
                resultados.add(construirResultado(fila.getJson(), fila.getTrackingNumber(),
                        "\"error\": \"JSON inválido: " + e.getMessage() + "\""));
                continue;
            }

            if (!correccion.aplica()) {
                log.info("Fila con tracking {} no contiene Store {}, se omite el envío", fila.getTrackingNumber(), STORE_ORIGEN);
                resultados.add(construirResultado(fila.getJson(), fila.getTrackingNumber(), MARCA_NO_APLICA));
                continue;
            }

            resultados.add(enviar(fila, correccion.json()));
            enviados++;
            pausar(enviados % TAMANO_LOTE == 0 ? PAUSA_CADA_LOTE_MS : PAUSA_ENTRE_ENVIOS_MS);
        }
        log.info("Finalizando reprocesar con {} resultados ({} enviados)", resultados.size(), enviados);
        return resultados;
    }

    private ReprocesoNodeResult enviar(ReprocesoNodeRow fila, String requestOriginal) {
        log.info("Enviando reproceso para tracking {}", fila.getTrackingNumber());
        try {
            String response = webClient.post()
                    .uri("/oms/sl/I200?origen=ecom")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestOriginal)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return construirResultado(requestOriginal, fila.getTrackingNumber(), response == null ? "" : response);
        } catch (WebClientResponseException e) {
            log.error("Error HTTP enviando tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            return construirResultado(requestOriginal, fila.getTrackingNumber(),
                    "\"error\": \"" + e.getMessage() + "\", \"body\": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error enviando tracking {}: {}", fila.getTrackingNumber(), e.getMessage());
            return construirResultado(requestOriginal, fila.getTrackingNumber(), "\"error\": \"" + e.getMessage() + "\"");
        }
    }

    private ResultadoCorreccion corregirStore(String json) throws Exception {
        JsonNode raiz = objectMapper.readTree(json);
        boolean aplica = false;
        JsonNode orderLines = raiz.path("OrderLines");
        for (JsonNode orderLine : orderLines) {
            JsonNode nodoOrderLine = orderLine.path("OrderLine");
            if (nodoOrderLine instanceof ObjectNode ordenNode
                    && STORE_ORIGEN.equals(ordenNode.path("Store").asText())) {
                ordenNode.put("Store", STORE_DESTINO);
                aplica = true;
            }
        }
        return new ResultadoCorreccion(aplica, objectMapper.writeValueAsString(raiz));
    }

    private ReprocesoNodeResult construirResultado(String requestOriginal, String trackingNumber, String response) {
        return ReprocesoNodeResult.builder()
                .requestOriginal(requestOriginal)
                .trackingNumber(trackingNumber)
                .response(response)
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

    private record ResultadoCorreccion(boolean aplica, String json) {
    }
}
