package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.mx.liverpool.automatizacionbackend.model.EstatusFulfillment;
import com.mx.liverpool.automatizacionbackend.model.FulfillmentResult;
import com.mx.liverpool.automatizacionbackend.payload.request.FulfillmentRequest;
import com.mx.liverpool.automatizacionbackend.payload.response.FulfillmentResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Log4j2
public class FulfillmentService {
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";
    private static final long PAUSA_GATEWAY_MS = 10_000L;
    private static final int MAX_RONDAS_REPROCESO = 3;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final FulfillmentService self;
    private final Map<String, EstatusFulfillment> estatusPorJob = new ConcurrentHashMap<>();
    private final Map<String, List<FulfillmentResult>> resultadosPorJob = new ConcurrentHashMap<>();

    @Autowired
    public FulfillmentService(WebClient.Builder webClient, ObjectMapper objectMapper, @Lazy FulfillmentService self) {
        this.webClient = webClient
                .baseUrl("https://ogcp-apigke-site-d.liverpool.com.mx")
                .build();
        this.objectMapper = objectMapper;
        this.self = self;
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

    public EstatusFulfillment iniciarReproceso(List<String> trackingNumbers) {
        log.info("Entrando a iniciarReproceso con {} trackingNumbers", trackingNumbers.size());

        String jobId = String.valueOf(System.currentTimeMillis());
        resultadosPorJob.put(jobId, new ArrayList<>());
        publicarEstatus(jobId, ESTATUS_EN_PROCESO, trackingNumbers.size(), 0, 0, 0, null, LocalDateTime.now(), null);

        self.procesarJob(jobId, trackingNumbers);

        log.info("Finalizando iniciarReproceso, job {} encolado", jobId);
        return estatusPorJob.get(jobId);
    }

    @Async("fulfillmentExecutor")
    public void procesarJob(String jobId, List<String> trackingNumbers) {
        log.info("Entrando a procesarJob para job {} con {} trackingNumbers", jobId, trackingNumbers.size());

        List<String> normalizados = trackingNumbers.stream()
                .map(this::rellenarDiezDigitos)
                .toList();

        int total = normalizados.size();
        LocalDateTime inicio = estatusPorJob.get(jobId).getInicio();
        List<FulfillmentResult> resultados = resultadosPorJob.get(jobId);
        List<String> diferidos = new ArrayList<>();
        int procesados = 0;
        int reprocesados = 0;

        // Pasada inicial: los que dan error de gateway (500/504) se difieren al final y se sigue avanzando.
        for (String tracking : normalizados) {
            publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados, 0, reprocesados, tracking, inicio, null);
            FulfillmentResult resultado = consultarFulfillment(tracking).block();
            if (esErrorGateway(resultado)) {
                log.warn("Tracking {} con error de gateway, se difiere al final tras pausa de {} ms", tracking, PAUSA_GATEWAY_MS);
                pausar();
                diferidos.add(tracking);
            } else {
                resultados.add(resultado);
                procesados++;
            }
        }

        // Reproceso: hasta MAX_RONDAS_REPROCESO sobre los diferidos.
        for (int ronda = 1; ronda <= MAX_RONDAS_REPROCESO && !diferidos.isEmpty(); ronda++) {
            log.info("Reproceso ronda {} para job {} con {} diferidos", ronda, jobId, diferidos.size());
            List<String> pendientes = new ArrayList<>();
            for (String tracking : diferidos) {
                publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados, pendientes.size(), reprocesados, tracking, inicio, null);
                FulfillmentResult resultado = consultarFulfillment(tracking).block();
                if (esErrorGateway(resultado)) {
                    log.warn("Tracking {} sigue con error de gateway en ronda {}", tracking, ronda);
                    pausar();
                    // En la última ronda ya no quedan más reprocesos: se conserva el resultado con error.
                    if (ronda == MAX_RONDAS_REPROCESO) {
                        resultados.add(resultado);
                    } else {
                        pendientes.add(tracking);
                    }
                } else {
                    resultados.add(resultado);
                    procesados++;
                    reprocesados++;
                }
            }
            diferidos = pendientes;
        }

        int conErrorGateway = diferidos.size() + (int) resultados.stream().filter(this::esErrorGateway).count();
        String estatusFinal = conErrorGateway == 0 ? ESTATUS_COMPLETADO : ESTATUS_COMPLETADO_CON_ERRORES;
        publicarEstatus(jobId, estatusFinal, total, procesados, conErrorGateway, reprocesados, null, inicio, LocalDateTime.now());
        log.info("Finalizando procesarJob para job {}: {} procesados, {} reprocesados, {} con error de gateway",
                jobId, procesados, reprocesados, conErrorGateway);
    }

    public EstatusFulfillment obtenerEstatus(String jobId) {
        log.info("Entrando a obtenerEstatus para job {}", jobId);
        EstatusFulfillment estatus = estatusPorJob.get(jobId);
        if (estatus == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        log.info("Finalizando obtenerEstatus para job {}", jobId);
        return estatus;
    }

    public List<FulfillmentResult> obtenerResultados(String jobId) {
        log.info("Entrando a obtenerResultados para job {}", jobId);
        List<FulfillmentResult> resultados = resultadosPorJob.get(jobId);
        if (resultados == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        List<FulfillmentResult> copia = List.copyOf(resultados);
        log.info("Finalizando obtenerResultados para job {} con {} resultados", jobId, copia.size());
        return copia;
    }

    private boolean esErrorGateway(FulfillmentResult resultado) {
        if (resultado == null) return false;
        String contenido = (resultado.getJson() != null ? resultado.getJson() : "")
                + (resultado.getResponse() != null ? resultado.getResponse() : "");
        return contenido.contains("500 Internal Server Error") || contenido.contains("504 Gateway Timeout");
    }

    private void pausar() {
        try {
            Thread.sleep(PAUSA_GATEWAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Pausa interrumpida: {}", e.getMessage());
        }
    }

    private void publicarEstatus(String jobId, String estatus, int total, int procesados, int conErrorGateway,
                                 int reprocesados, String trackingActual, LocalDateTime inicio, LocalDateTime fin) {
        estatusPorJob.put(jobId, EstatusFulfillment.builder()
                .jobId(jobId)
                .estatus(estatus)
                .totalTrackings(total)
                .procesados(procesados)
                .conErrorGateway(conErrorGateway)
                .reprocesados(reprocesados)
                .trackingActual(trackingActual)
                .inicio(inicio)
                .fin(fin)
                .build());
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
