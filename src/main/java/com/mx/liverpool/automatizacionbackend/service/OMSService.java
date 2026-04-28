package com.mx.liverpool.automatizacionbackend.service;

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
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class OMSService {
    private final OMSRepository omsRepository;
    private final WebClient webClient;

    @Autowired
    public OMSService(OMSRepository omsRepository, WebClient.Builder webClient) {
        this.omsRepository = omsRepository;
        this.webClient = webClient
                .baseUrl("https://apigee-pro.liverpool.com.mx")
                .defaultHeader("apikey", "ODz9esMz5TU3EEe0S7gad7V8OgXQN1cYnWfehDn3hIhnZ9hf")
                .build();
    }

    public void obtenerReporteNoOMS() {
        LocalDateTime fechaFin = LocalDateTime.now();
        LocalDateTime fechaInicio = fechaFin.minusDays(1);

        fechaInicio = fechaInicio.withHour(0).withMinute(0).withSecond(0).withNano(0);
        fechaFin = fechaFin.withHour(0).withMinute(0).withSecond(0).withNano(0);

        log.info("Fecha inicio: {}, fecha fin: {}", fechaInicio, fechaFin);
        List<NoOMS> result = omsRepository.obtenerOrdenesNoOMS(fechaInicio, fechaFin);
        for (NoOMS noOMS : result) {
            log.info("NoOMS: {}", noOMS);
            if (noOMS.getTotal() > 90) {
                log.warn("Alerta: NoOMS con total mayor a 90: {}", noOMS);
            }
        }
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
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("status", response.getStatusCode().value());
                    result.put("responseBody", response.getBody());
                    return result;
                })
                .onErrorResume(e -> {
                    log.error("Error llamando a la API para la orden {}: {}", request.getOrderNo(), e.getMessage());
                    Map<String, Object> errorResult = new java.util.HashMap<>();
                    errorResult.put("status", 500);
                    errorResult.put("responseBody", e.getMessage());
                    return Mono.just(errorResult);
                });
    }
}
