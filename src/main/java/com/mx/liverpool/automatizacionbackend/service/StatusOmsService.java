package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.mx.liverpool.automatizacionbackend.model.EstatusFulfillment;
import com.mx.liverpool.automatizacionbackend.model.StatusOmsResult;
import com.mx.liverpool.automatizacionbackend.payload.request.FulfillmentRequest;
import com.mx.liverpool.automatizacionbackend.payload.response.FulfillmentResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Log4j2
public class StatusOmsService {
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";
    private static final String ERROR_PETICION = "ERROR_PETICION";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final StatusOmsService self;
    private final int maxRondas;
    private final Map<String, EstatusFulfillment> estatusPorJob = new ConcurrentHashMap<>();
    private final Map<String, List<StatusOmsResult>> resultadosPorJob = new ConcurrentHashMap<>();

    @Autowired
    public StatusOmsService(WebClient.Builder webClient, ObjectMapper objectMapper, @Lazy StatusOmsService self,
                            @Value("${status-oms.max-rondas-reproceso}") int maxRondas) {
        this.webClient = webClient
                .baseUrl("https://ogcp-apigke-site-d.liverpool.com.mx")
                .build();
        this.objectMapper = objectMapper;
        this.self = self;
        this.maxRondas = maxRondas;
    }

    public EstatusFulfillment iniciarProceso(List<String> trackingNumbers) {
        log.info("Entrando a iniciarProceso con {} trackingNumbers", trackingNumbers.size());

        String jobId = String.valueOf(System.currentTimeMillis());
        resultadosPorJob.put(jobId, new ArrayList<>());
        publicarEstatus(jobId, ESTATUS_EN_PROCESO, trackingNumbers.size(), 0, 0, 0, null, LocalDateTime.now(), null);

        self.procesarJob(jobId, trackingNumbers);

        log.info("Finalizando iniciarProceso, job {} encolado", jobId);
        return estatusPorJob.get(jobId);
    }

    @Async("statusOmsExecutor")
    public void procesarJob(String jobId, List<String> trackingNumbers) {
        log.info("Entrando a procesarJob para job {} con {} trackingNumbers", jobId, trackingNumbers.size());

        List<String> normalizados = trackingNumbers.stream()
                .map(this::rellenarDiezDigitos)
                .toList();

        int total = normalizados.size();
        LocalDateTime inicio = estatusPorJob.get(jobId).getInicio();
        List<StatusOmsResult> resultados = resultadosPorJob.get(jobId);
        List<String> diferidos = new ArrayList<>();
        int procesados = 0;
        int reprocesados = 0;

        // Pasada inicial: el que falla se encola al final y se sigue avanzando, sin pausas.
        for (String tracking : normalizados) {
            publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados, 0, reprocesados, tracking, inicio, null);
            StatusOmsResult resultado = consultarStatusOms(tracking).block();
            if (esErrorGateway(resultado)) {
                log.warn("Tracking {} con error de petición, se difiere al final", tracking);
                diferidos.add(tracking);
            } else {
                resultados.add(resultado);
                procesados++;
            }
        }

        // Reproceso: hasta maxRondas sobre los diferidos.
        for (int ronda = 1; ronda <= maxRondas && !diferidos.isEmpty(); ronda++) {
            log.info("Reproceso ronda {} para job {} con {} diferidos", ronda, jobId, diferidos.size());
            List<String> pendientes = new ArrayList<>();
            for (String tracking : diferidos) {
                publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados, pendientes.size(), reprocesados, tracking, inicio, null);
                StatusOmsResult resultado = consultarStatusOms(tracking).block();
                if (esErrorGateway(resultado)) {
                    log.warn("Tracking {} sigue con error de petición en la ronda {}", tracking, ronda);
                    // En la última ronda ya no quedan más reprocesos: se conserva el resultado con error.
                    if (ronda == maxRondas) {
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
        log.info("Finalizando procesarJob para job {}: {} procesados, {} reprocesados, {} con error",
                jobId, procesados, reprocesados, conErrorGateway);
    }

    public EstatusFulfillment obtenerEstatus(String jobId) {
        log.info("Entrando a obtenerEstatus para job {}", jobId);
        EstatusFulfillment estatus = estatusPorJob.get(jobId);
        if (estatus == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        log.info("Finalizando obtenerEstatus para job {}", jobId);
        return estatus;
    }

    public List<EstatusFulfillment> obtenerJobs() {
        log.info("Entrando a obtenerJobs");
        // Los jobs viven en memoria desde el último arranque; el más reciente primero.
        List<EstatusFulfillment> jobs = estatusPorJob.values().stream()
                .sorted(Comparator.comparing(EstatusFulfillment::getInicio).reversed())
                .toList();
        log.info("Finalizando obtenerJobs con {} jobs", jobs.size());
        return jobs;
    }

    public List<StatusOmsResult> obtenerResultados(String jobId) {
        log.info("Entrando a obtenerResultados para job {}", jobId);
        List<StatusOmsResult> resultados = resultadosPorJob.get(jobId);
        if (resultados == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        List<StatusOmsResult> copia = List.copyOf(resultados);
        log.info("Finalizando obtenerResultados para job {} con {} resultados", jobId, copia.size());
        return copia;
    }

    private boolean esErrorGateway(StatusOmsResult resultado) {
        // Solo se reintenta la petición que falló; UNKNOWN, null y PARSE_ERROR son respuestas del gateway.
        return resultado != null && ERROR_PETICION.equals(resultado.getStatusOms());
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

    private Mono<String> llamarFulfillment(String trackingNumber) {
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
                .bodyToMono(String.class);
    }

    private Mono<StatusOmsResult> consultarStatusOms(String trackingNumber) {
        return llamarFulfillment(trackingNumber)
                .map(json -> StatusOmsResult.builder()
                        .trackingNumber(trackingNumber)
                        .statusOms(extraerStatusOms(json))
                        .build())
                .onErrorResume(e -> {
                    String body = e instanceof WebClientResponseException w ? w.getResponseBodyAsString() : "";
                    log.error("Error consultando statusOms para el tracking {}: {} - {}", trackingNumber, e.getMessage(), body);
                    return Mono.just(StatusOmsResult.builder()
                            .trackingNumber(trackingNumber)
                            .statusOms(ERROR_PETICION)
                            .build());
                });
    }

    private String extraerStatusOms(String json) {
        // Jackson no distingue la llave ausente de la llave en null; el contains sí, y el formato original lo separa.
        if (json == null || !json.contains("\"statusOms\"")) return "UNKNOWN";
        try {
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, FulfillmentResponse.class);
            List<FulfillmentResponse> responses = objectMapper.readValue(json, listType);
            if (responses.isEmpty()) return "UNKNOWN";
            String statusOms = responses.getFirst().getStatusOms();
            return statusOms == null ? "null" : statusOms;
        } catch (Exception e) {
            log.error("Error parseando statusOms: {}", e.getMessage());
            return "PARSE_ERROR";
        }
    }
}
