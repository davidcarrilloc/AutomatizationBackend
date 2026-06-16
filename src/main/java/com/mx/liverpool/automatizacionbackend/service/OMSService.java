package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.liverpool.automatizacionbackend.model.NoOMS;
import com.mx.liverpool.automatizacionbackend.payload.request.OrderRequest;
import com.mx.liverpool.automatizacionbackend.repository.OMSRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class OMSService {
    private final OMSRepository omsRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OMSService(OMSRepository omsRepository, WebClient.Builder webClient, ObjectMapper objectMapper) {
        this.omsRepository = omsRepository;
        this.objectMapper = objectMapper;
        this.webClient = webClient
                .baseUrl("https://apigee-pro.liverpool.com.mx")
                .defaultHeader("apikey", "ODz9esMz5TU3EEe0S7gad7V8OgXQN1cYnWfehDn3hIhnZ9hf")
                .build();
    }

    public List<NoOMS> obtenerReporteNoOMS() {
        LocalDateTime fechaFin = LocalDateTime.now();
        LocalDateTime fechaInicio = fechaFin.minusDays(1);

        fechaInicio = fechaInicio.withHour(0).withMinute(0).withSecond(0).withNano(0);
        fechaFin = fechaFin.withHour(0).withMinute(0).withSecond(0).withNano(0);

        log.info("Fecha inicio: {}, fecha fin: {}", fechaInicio, fechaFin);
        return omsRepository.obtenerOrdenesNoOMS(fechaInicio, fechaFin);
    }

    public Map<String, Map<String, Object>> massivePostOrder(List<Map<Integer, String>> ordenesVenta, String sbbOrLp) {
        return Flux.fromIterable(ordenesVenta)
                .delayElements(Duration.ofMillis(500))
                .flatMap(orden -> {
                    String ordenVenta = orden.get(0);

                    OrderRequest orderRequest = OrderRequest.builder()
                            .orderNo(ordenVenta)
                            .enterpriseCode(sbbOrLp)
                            .documentType("0001")
                            .build();

                    return postOrder(orderRequest)
                            .map(data -> Map.entry(ordenVenta, data));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .block();
    }

    private Mono<Map<String, Object>> postOrder(OrderRequest request) {
        return webClient.post()
                .uri("/oms/v1/int/2010")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .toEntity(String.class)
                .map(response -> {
                    int status = response.getStatusCode().value();
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("status", status);
                    result.put("responseBody", response.getBody());
                    result.put("orderStatuses", status == 200 ? extraerOrderStatuses(response.getBody()) : "");
                    return result;
                })
                .onErrorResume(e -> {
                    log.error("Error llamando a la API para la orden {}: {}", request.getOrderNo(), e.getMessage());
                    Map<String, Object> errorResult = new java.util.HashMap<>();
                    errorResult.put("status", 400);
                    errorResult.put("responseBody", e.getMessage());
                    errorResult.put("orderStatuses", "");
                    return Mono.just(errorResult);
                });
    }

    private String extraerOrderStatuses(String responseBody) {
        log.info("Entrando a extraerOrderStatuses");
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode orderLines = root.path("OrderLines");

            List<JsonNode> statuses = new ArrayList<>();
            if (orderLines.isArray()) {
                for (JsonNode orderLine : orderLines) {
                    JsonNode orderStatuses = orderLine.path("OrderLine").path("OrderStatuses");
                    if (!orderStatuses.isMissingNode() && !orderStatuses.isNull()) {
                        statuses.add(orderStatuses);
                    }
                }
            }

            log.info("Finalizando extraerOrderStatuses con {} nodos OrderStatuses", statuses.size());
            return objectMapper.writeValueAsString(statuses);
        } catch (Exception e) {
            log.error("Error extrayendo el nodo OrderStatuses: {}", e.getMessage());
            return "";
        }
    }
}
