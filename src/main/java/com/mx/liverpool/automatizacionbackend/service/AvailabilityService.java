package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.liverpool.automatizacionbackend.model.AvailabilityResult;
import com.mx.liverpool.automatizacionbackend.model.AvailabilityRow;
import com.mx.liverpool.automatizacionbackend.model.EstatusFulfillment;
import com.mx.liverpool.automatizacionbackend.payload.response.TokenResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Log4j2
public class AvailabilityService {
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";

    private final WebClient ogcpClient;
    private final WebClient ctClient;
    private final ObjectMapper objectMapper;
    private final AvailabilityService self;
    private final String tokenUrl;
    private final String clientId;
    private final String projectKey;
    private final long pausaConsultaMs;
    private final long pausaGatewayMs;
    private final int maxRondasReproceso;
    private final Map<String, EstatusFulfillment> estatusPorJob = new ConcurrentHashMap<>();
    private final Map<String, List<AvailabilityResult>> resultadosPorJob = new ConcurrentHashMap<>();

    @Autowired
    public AvailabilityService(WebClient.Builder webClientBuilder,
                               ObjectMapper objectMapper,
                               @Lazy AvailabilityService self,
                               @Value("${availability.ogcp.base-url}") String ogcpBaseUrl,
                               @Value("${availability.ct.base-url}") String ctBaseUrl,
                               @Value("${availability.ct.token-url}") String tokenUrl,
                               @Value("${availability.ct.client-id}") String clientId,
                               @Value("${availability.ct.project-key}") String projectKey,
                               @Value("${availability.pausa-consulta-ms}") long pausaConsultaMs,
                               @Value("${availability.pausa-gateway-ms}") long pausaGatewayMs,
                               @Value("${availability.max-rondas-reproceso}") int maxRondasReproceso) {
        this.ogcpClient = webClientBuilder.baseUrl(ogcpBaseUrl).build();
        // Con los expand la orden rebasa el límite por defecto de 256 KB del codec de WebClient.
        this.ctClient = webClientBuilder
                .baseUrl(ctBaseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;
        this.self = self;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.projectKey = projectKey;
        this.pausaConsultaMs = pausaConsultaMs;
        this.pausaGatewayMs = pausaGatewayMs;
        this.maxRondasReproceso = maxRondasReproceso;
    }

    public EstatusFulfillment iniciarReproceso(List<AvailabilityRow> filas, String password) {
        log.info("Entrando a iniciarReproceso de availability con {} filas", filas.size());

        // El token se pide aquí y no dentro del job: si la contraseña es incorrecta el usuario lo ve al momento.
        String token = obtenerToken(password);

        String jobId = String.valueOf(System.currentTimeMillis());
        resultadosPorJob.put(jobId, new ArrayList<>());
        publicarEstatus(jobId, ESTATUS_EN_PROCESO, filas.size(), 0, 0, 0, null, LocalDateTime.now(), null);

        self.procesarJob(jobId, filas, token);

        log.info("Finalizando iniciarReproceso de availability, job {} encolado", jobId);
        return estatusPorJob.get(jobId);
    }

    @Async("fulfillmentExecutor")
    public void procesarJob(String jobId, List<AvailabilityRow> filas, String token) {
        log.info("Entrando a procesarJob de availability para job {} con {} filas", jobId, filas.size());

        int total = filas.size();
        LocalDateTime inicio = estatusPorJob.get(jobId).getInicio();
        List<AvailabilityResult> resultados = resultadosPorJob.get(jobId);
        List<AvailabilityRow> diferidos = new ArrayList<>();
        int procesados = 0;
        int reprocesados = 0;

        // Pasada inicial: las filas con error de gateway se difieren al final y se sigue avanzando.
        for (AvailabilityRow fila : filas) {
            publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados, diferidos.size(), reprocesados, fila.getRemision(), inicio, null);
            AvailabilityResult resultado = consultarAvailability(fila, token);
            if (esErrorGateway(resultado)) {
                log.warn("Remisión {} con error de gateway, se difiere al final tras pausa de {} ms", fila.getRemision(), pausaGatewayMs);
                pausar(pausaGatewayMs);
                diferidos.add(fila);
            } else {
                resultados.add(resultado);
                procesados++;
                pausar(pausaConsultaMs);
            }
        }

        // Reproceso: hasta maxRondasReproceso sobre los diferidos.
        for (int ronda = 1; ronda <= maxRondasReproceso && !diferidos.isEmpty(); ronda++) {
            log.info("Reproceso de availability ronda {} para job {} con {} diferidos", ronda, jobId, diferidos.size());
            List<AvailabilityRow> pendientes = new ArrayList<>();
            for (AvailabilityRow fila : diferidos) {
                publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados, diferidos.size(), reprocesados, fila.getRemision(), inicio, null);
                AvailabilityResult resultado = consultarAvailability(fila, token);
                if (esErrorGateway(resultado)) {
                    log.warn("Remisión {} sigue con error de gateway en la ronda {}", fila.getRemision(), ronda);
                    pausar(pausaGatewayMs);
                    // En la última ronda ya no quedan más reprocesos: se conserva el resultado con error.
                    if (ronda == maxRondasReproceso) resultados.add(resultado);
                    else pendientes.add(fila);
                } else {
                    resultados.add(resultado);
                    procesados++;
                    reprocesados++;
                    pausar(pausaConsultaMs);
                }
            }
            diferidos = pendientes;
        }

        int conErrorGateway = (int) resultados.stream().filter(this::esErrorGateway).count();
        String estatusFinal = conErrorGateway == 0 ? ESTATUS_COMPLETADO : ESTATUS_COMPLETADO_CON_ERRORES;
        publicarEstatus(jobId, estatusFinal, total, procesados, conErrorGateway, reprocesados, null, inicio, LocalDateTime.now());
        log.info("Finalizando procesarJob de availability para job {}: {} procesados, {} reprocesados, {} con error de gateway",
                jobId, procesados, reprocesados, conErrorGateway);
    }

    public EstatusFulfillment obtenerEstatus(String jobId) {
        log.info("Entrando a obtenerEstatus de availability para job {}", jobId);
        EstatusFulfillment estatus = estatusPorJob.get(jobId);
        if (estatus == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        log.info("Finalizando obtenerEstatus de availability para job {}", jobId);
        return estatus;
    }

    public List<EstatusFulfillment> obtenerJobs() {
        log.info("Entrando a obtenerJobs de availability");
        // Los jobs viven en memoria desde el último arranque; el más reciente primero.
        List<EstatusFulfillment> jobs = estatusPorJob.values().stream()
                .sorted(Comparator.comparing(EstatusFulfillment::getInicio).reversed())
                .toList();
        log.info("Finalizando obtenerJobs de availability con {} jobs", jobs.size());
        return jobs;
    }

    public List<AvailabilityResult> obtenerResultados(String jobId) {
        log.info("Entrando a obtenerResultados de availability para job {}", jobId);
        List<AvailabilityResult> resultados = resultadosPorJob.get(jobId);
        if (resultados == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        List<AvailabilityResult> copia = List.copyOf(resultados);
        log.info("Finalizando obtenerResultados de availability para job {} con {} resultados", jobId, copia.size());
        return copia;
    }

    /** El password llega por parámetro del endpoint: no se guarda ni se escribe en bitácora. */
    private String obtenerToken(String password) {
        TokenResponse token = ctClient.post()
                .uri(URI.create(tokenUrl))
                .headers(headers -> headers.setBasicAuth(clientId, password))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();

        if (token == null || token.getAccessToken() == null || token.getAccessToken().isBlank())
            throw new IllegalStateException("No se pudo obtener el access_token de commercetools");

        return token.getAccessToken();
    }

    private AvailabilityResult consultarAvailability(AvailabilityRow fila, String token) {
        String remision = rellenarDiezDigitos(fila.getRemision());
        String stock;
        try {
            stock = extraerStock(obtenerOrden(obtenerCtOrderId(remision), token), fila.getSku());
        } catch (Exception e) {
            String body = e instanceof WebClientResponseException w ? w.getResponseBodyAsString() : "";
            log.error("Error consultando availability para la remisión {}: {} - {}", remision, e.getMessage(), body);
            stock = "\"error\": \"" + e.getMessage() + "\"";
        }

        return AvailabilityResult.builder()
                .sku(fila.getSku())
                .remision(fila.getRemision())
                .stock(stock)
                .build();
    }

    private String obtenerCtOrderId(String remision) throws JsonProcessingException {
        String json = ogcpClient.get()
                .uri("/order-service/v1/order/tracking-number/{remision}", remision)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (json == null || json.isBlank())
            throw new IllegalStateException("La remisión no devolvió respuesta");

        JsonNode ctOrderId = objectMapper.readTree(json).path("ctOrderId");
        if (ctOrderId.isMissingNode() || ctOrderId.asText().isBlank())
            throw new IllegalStateException("La remisión no tiene ctOrderId");

        return ctOrderId.asText();
    }

    private String obtenerOrden(String ctOrderId, String token) {
        return ctClient.get()
                .uri(builder -> builder
                        .path("/{projectKey}/orders/{ctOrderId}")
                        .queryParam("expand", "paymentInfo.payments[*]")
                        .queryParam("expand", "lineItems[*].supplyChannel")
                        .build(projectKey, ctOrderId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /** Devuelve la cadena completa {@code "availableQuantity": 37,} del lineItem cuyo sku empata. */
    String extraerStock(String json, String sku) {
        try {
            for (JsonNode lineItem : objectMapper.readTree(json).path("lineItems")) {
                JsonNode variant = lineItem.path("variant");
                if (!sku.equals(variant.path("sku").asText())) continue;

                JsonNode cantidad = variant.path("availability").path("availableQuantity");
                if (cantidad.isMissingNode() || cantidad.isNull())
                    return "\"error\": \"El SKU no trae availability.availableQuantity\"";

                return "\"availableQuantity\": " + cantidad.asText() + ",";
            }
        } catch (JsonProcessingException e) {
            return "\"error\": \"" + e.getOriginalMessage() + "\"";
        }
        return "\"error\": \"SKU no encontrado en la orden\"";
    }

    private boolean esErrorGateway(AvailabilityResult resultado) {
        if (resultado == null || resultado.getStock() == null) return false;
        return resultado.getStock().contains("500 Internal Server Error")
                || resultado.getStock().contains("504 Gateway Timeout");
    }

    private void pausar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Pausa interrumpida: {}", e.getMessage());
        }
    }

    private void publicarEstatus(String jobId, String estatus, int total, int procesados, int conErrorGateway,
                                 int reprocesados, String remisionActual, LocalDateTime inicio, LocalDateTime fin) {
        estatusPorJob.put(jobId, EstatusFulfillment.builder()
                .jobId(jobId)
                .estatus(estatus)
                .totalTrackings(total)
                .procesados(procesados)
                .conErrorGateway(conErrorGateway)
                .reprocesados(reprocesados)
                .trackingActual(remisionActual)
                .inicio(inicio)
                .fin(fin)
                .build());
    }

    private String rellenarDiezDigitos(String remision) {
        if (remision == null) return null;
        String limpio = remision.trim();
        return limpio.length() < 10 ? "0".repeat(10 - limpio.length()) + limpio : limpio;
    }
}
