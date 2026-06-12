package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.mx.liverpool.automatizacionbackend.model.FulfillmentResult;
import com.mx.liverpool.automatizacionbackend.payload.request.FulfillmentRequest;
import com.mx.liverpool.automatizacionbackend.payload.response.FulfillmentResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class FulfillmentService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public FulfillmentService(WebClient.Builder webClient, ObjectMapper objectMapper) {
        this.webClient = webClient
                .baseUrl("https://ogcp-apigke-site-d.liverpool.com.mx")
                .build();
        this.objectMapper = objectMapper;
    }

    public List<FulfillmentResult> procesarFulfillment(List<String> trackingNumbers) {
        log.info("Entrando a procesarFulfillment con {} trackingNumbers", trackingNumbers.size());
        List<String> normalizados = trackingNumbers.stream()
                .map(this::rellenarDiezDigitos)
                .toList();
        List<FulfillmentResult> resultados = Flux.fromIterable(normalizados)
                .delayElements(Duration.ofMillis(500))
                .flatMap(this::consultarFulfillment)
                .collectList()
                .block();
        log.info("Finalizando procesarFulfillment");
        return resultados;
    }

    private String rellenarDiezDigitos(String trackingNumber) {
        if (trackingNumber == null) return null;
        String limpio = trackingNumber.trim();
        return limpio.length() < 10 ? "0".repeat(10 - limpio.length()) + limpio : limpio;
    }

    private Mono<FulfillmentResult> consultarFulfillment(String trackingNumber) {
        FulfillmentRequest request = FulfillmentRequest.builder()
                .processes(List.of(FulfillmentRequest.Process.builder()
                        .trackingNumber(trackingNumber)
                        .build()))
                .build();

        return webClient.post()
                .uri("/order-service/v1/order/fulFillment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> construirResultado(trackingNumber, json))
                .onErrorResume(e -> {
                    log.error("Error consultando fulfillment para el tracking {}: {}", trackingNumber, e.getMessage());
                    return Mono.just(FulfillmentResult.builder()
                            .trackingNumber(trackingNumber)
                            .response("\"error\": \"" + e.getMessage() + "\",")
                            .json(e.getMessage())
                            .build());
                });
    }

    private FulfillmentResult construirResultado(String trackingNumber, String json) {
        String response;
        try {
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, FulfillmentResponse.class);
            List<FulfillmentResponse> responses = objectMapper.readValue(json, listType);

            FulfillmentResponse primero = responses.isEmpty() ? new FulfillmentResponse() : responses.getFirst();
            response = primero.getError() != null
                    ? "\"error\": \"" + primero.getError() + "\","
                    : concatenarSuccess(primero);
        } catch (Exception e) {
            log.error("Error parseando respuesta de fulfillment para el tracking {}: {}", trackingNumber, e.getMessage());
            response = "\"error\": \"" + e.getMessage() + "\",";
        }

        return FulfillmentResult.builder()
                .trackingNumber(trackingNumber)
                .response(response)
                .json(json)
                .build();
    }

    private String concatenarSuccess(FulfillmentResponse r) {
        Map<String, String> estatus = new LinkedHashMap<>();
        estatus.put("statusSoms", r.getStatusSoms());
        estatus.put("statusMkp", r.getStatusMkp());
        estatus.put("statusSterling", r.getStatusSterling());
        estatus.put("statusPendingOrder", r.getStatusPendingOrder());
        estatus.put("statusOms", r.getStatusOms());
        estatus.put("statusProtec", r.getStatusProtec());
        estatus.put("statusMyPurchases", r.getStatusMyPurchases());
        estatus.put("statusFirebase", r.getStatusFirebase());
        estatus.put("statusPreBackOrder", r.getStatusPreBackOrder());
        estatus.put("statusEmail", r.getStatusEmail());

        return estatus.entrySet().stream()
                .filter(e -> "SUCCESS".equalsIgnoreCase(e.getValue()))
                .map(e -> "\"" + e.getKey() + "\": \"" + e.getValue() + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
