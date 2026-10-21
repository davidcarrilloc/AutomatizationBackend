package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.ApvLogOrden;
import com.mx.liverpool.automatizacionbackend.model.ApvResult;
import com.mx.liverpool.automatizacionbackend.model.EstatusFulfillment;
import com.mx.liverpool.automatizacionbackend.repository.ApvRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reproceso de remisiones APV por jobId: un solo resend a Sterling (ccenter) con todas las remisiones,
 * dos consultas a APPSALESMAN (WLVPRO) para rescatar el JSON de cada una y envío tal cual a la fachada
 * I200. La fila con error de fachada se difiere al final y se reintenta hasta max-rondas.
 */
@Service
@Log4j2
public class ApvService {
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";
    // Oracle admite hasta 1000 elementos en un IN.
    private static final int TAMANO_LOTE_IN = 1000;

    private final WebClient webClient;
    private final ApvRepository apvRepository;
    private final ReprocesoService reprocesoService;
    private final ApvService self;
    private final long pausaErrorMs;
    private final int maxRondas;
    private final Map<String, EstatusFulfillment> estatusPorJob = new ConcurrentHashMap<>();
    private final Map<String, List<ApvResult>> resultadosPorJob = new ConcurrentHashMap<>();

    @Autowired
    public ApvService(WebClient.Builder webClientBuilder,
                      ApvRepository apvRepository,
                      ReprocesoService reprocesoService,
                      @Lazy ApvService self,
                      @Value("${apv.resend.base-url}") String resendBaseUrl,
                      @Value("${apv.pausa-error-ms}") long pausaErrorMs,
                      @Value("${apv.max-rondas}") int maxRondas) {
        this.webClient = webClientBuilder
                .baseUrl(resendBaseUrl)
                .build();
        this.apvRepository = apvRepository;
        this.reprocesoService = reprocesoService;
        this.self = self;
        this.pausaErrorMs = pausaErrorMs;
        this.maxRondas = maxRondas;
    }

    public EstatusFulfillment iniciarReproceso(List<String> filas) {
        log.info("Entrando a iniciarReproceso de APV con {} filas", filas.size());

        // Solo dígitos: descarta el encabezado y celdas de texto sin configurar nada.
        List<String> remisiones = filas.stream()
                .filter(remision -> remision.matches("\\d+"))
                .toList();
        if (remisiones.isEmpty()) throw new IllegalArgumentException("El Excel no trae remisiones en la columna A.");

        String jobId = String.valueOf(System.currentTimeMillis());
        resultadosPorJob.put(jobId, new ArrayList<>());
        publicarEstatus(jobId, ESTATUS_EN_PROCESO, remisiones.size(), 0, 0, 0, null, LocalDateTime.now(), null);

        self.procesarJob(jobId, remisiones);

        log.info("Finalizando iniciarReproceso de APV, job {} encolado", jobId);
        return estatusPorJob.get(jobId);
    }

    @Async("statusOmsExecutor")
    public void procesarJob(String jobId, List<String> remisiones) {
        log.info("Entrando a procesarJob de APV para job {} con {} remisiones", jobId, remisiones.size());

        int total = remisiones.size();
        verificarVigente(jobId);
        LocalDateTime inicio = estatusPorJob.get(jobId).getInicio();

        // Un solo resend con todas las remisiones; si falla se anota y el job continúa.
        String resend = reenviarASterling(remisiones);

        // Dos consultas por corrida (en lotes de 1000): transacciones de todas las remisiones y luego
        // los JSON de esas transacciones. Si truenan, todas las filas quedan con ese error y no se envía nada.
        Map<String, String> transaccionPorRemision = new HashMap<>();
        Map<String, String> jsonPorTransaccion = new HashMap<>();
        String errorConsulta = null;
        try {
            transaccionPorRemision = mapearTransacciones(
                    consultarEnLotes(remisiones.stream().distinct().toList(), apvRepository::obtenerTransacciones));
            log.info("LOG_ORDER devolvió transacción para {} de {} remisiones", transaccionPorRemision.size(), total);
            jsonPorTransaccion = mapearJson(
                    consultarEnLotes(List.copyOf(transaccionPorRemision.values()), apvRepository::obtenerJsonSterling));
            log.info("LOG_ORDER_AUDIT devolvió JSON para {} de {} transacciones", jsonPorTransaccion.size(), transaccionPorRemision.size());
        } catch (Exception e) {
            log.error("Error consultando APPSALESMAN para job {}: {}", jobId, e.getMessage());
            errorConsulta = "\"error\": \"" + e.getMessage() + "\"";
        }

        AtomicInteger procesados = new AtomicInteger();
        AtomicInteger reprocesados = new AtomicInteger();
        AtomicInteger conError = new AtomicInteger();

        final Map<String, String> transacciones = transaccionPorRemision;
        final Map<String, String> jsons = jsonPorTransaccion;
        final String errorDeConsulta = errorConsulta;
        List<ApvResult> resultados = procesarConRondas(
                remisiones,
                remision -> enviarRemision(resolver(remision, resend, transacciones, jsons, errorDeConsulta)),
                maxRondas,
                procesados, reprocesados, conError,
                this::pausar,
                remision -> publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados.get(),
                        conError.get(), reprocesados.get(), remision, inicio, null));

        verificarVigente(jobId);
        resultadosPorJob.put(jobId, resultados);
        String estatusFinal = conError.get() == 0 && !esError(resend) ? ESTATUS_COMPLETADO : ESTATUS_COMPLETADO_CON_ERRORES;
        publicarEstatus(jobId, estatusFinal, total, procesados.get(), conError.get(), reprocesados.get(), null, inicio, LocalDateTime.now());
        log.info("Finalizando procesarJob de APV para job {}: {} procesados, {} reprocesados, {} con error",
                jobId, procesados.get(), reprocesados.get(), conError.get());
    }

    /**
     * Pasada única conservando el orden de entrada. La remisión con error de fachada se difiere al final
     * y se reintenta hasta maxRondas (en la última se conserva el error); la que no trae JSON es error
     * definitivo, sin pausa. Estático y sin estado para poder probarse sin arrancar la app.
     */
    static List<ApvResult> procesarConRondas(List<String> remisiones,
                                             Function<String, ApvResult> procesar,
                                             int maxRondas,
                                             AtomicInteger procesados, AtomicInteger reprocesados, AtomicInteger conError,
                                             Runnable pausaError,
                                             Consumer<String> publicarAvance) {
        ApvResult[] porPosicion = new ApvResult[remisiones.size()];
        Deque<Pendiente> diferidos = new ArrayDeque<>();

        for (int i = 0; i < remisiones.size(); i++) {
            String remision = remisiones.get(i);
            publicarAvance.accept(remision);
            ApvResult resultado = procesar.apply(remision);
            if (esReintentable(resultado) && maxRondas > 0) {
                pausaError.run();
                diferidos.add(new Pendiente(i, remision, 1));
            } else {
                porPosicion[i] = resultado;
                procesados.incrementAndGet();
                if (esError(resultado.getError())) conError.incrementAndGet();
            }
        }

        while (!diferidos.isEmpty()) {
            Pendiente pendiente = diferidos.poll();
            publicarAvance.accept(pendiente.remision());
            ApvResult resultado = procesar.apply(pendiente.remision());
            if (esReintentable(resultado) && pendiente.ronda() < maxRondas) {
                pausaError.run();
                diferidos.add(new Pendiente(pendiente.posicion(), pendiente.remision(), pendiente.ronda() + 1));
            } else {
                porPosicion[pendiente.posicion()] = resultado;
                procesados.incrementAndGet();
                if (esError(resultado.getError())) conError.incrementAndGet();
                else reprocesados.incrementAndGet();
            }
        }

        return new ArrayList<>(Arrays.asList(porPosicion));
    }

    private List<ApvLogOrden> consultarEnLotes(List<String> llaves, Function<List<String>, List<ApvLogOrden>> consulta) {
        List<ApvLogOrden> filas = new ArrayList<>();
        for (int i = 0; i < llaves.size(); i += TAMANO_LOTE_IN) {
            filas.addAll(consulta.apply(llaves.subList(i, Math.min(i + TAMANO_LOTE_IN, llaves.size()))));
        }
        return filas;
    }

    /** Remisión → transacción más reciente. Las filas llegan por CREATED_AT DESC: gana la primera de cada remisión. */
    static Map<String, String> mapearTransacciones(List<ApvLogOrden> filas) {
        Map<String, String> transaccionPorRemision = new HashMap<>();
        for (ApvLogOrden fila : filas) {
            if (fila.getOrderNumber() == null || fila.getTransactionId() == null) continue;
            transaccionPorRemision.putIfAbsent(fila.getOrderNumber().trim(), fila.getTransactionId());
        }
        return transaccionPorRemision;
    }

    /** Transacción → primer JSON no vacío. */
    static Map<String, String> mapearJson(List<ApvLogOrden> filas) {
        Map<String, String> jsonPorTransaccion = new HashMap<>();
        for (ApvLogOrden fila : filas) {
            String json = fila.getSterlingRawRequestJson();
            if (fila.getTransactionId() == null || json == null || json.isBlank()) continue;
            jsonPorTransaccion.putIfAbsent(fila.getTransactionId(), json);
        }
        return jsonPorTransaccion;
    }

    /** Empata remisión → transacción → JSON. Sin JSON deja el error definitivo; con JSON queda lista para enviar. */
    static ApvResult resolver(String remision, String resend, Map<String, String> transaccionPorRemision,
                              Map<String, String> jsonPorTransaccion, String errorConsulta) {
        if (errorConsulta != null) return construirResultado(remision, resend, null, null, errorConsulta);

        String transactionId = transaccionPorRemision.get(remision.trim());
        if (transactionId == null) {
            return construirResultado(remision, resend, null, null, "\"error\": \"Sin TRANSACTION_ID en LOG_ORDER\"");
        }
        String json = jsonPorTransaccion.get(transactionId);
        if (json == null) {
            return construirResultado(remision, resend, null, null,
                    "\"error\": \"Sin STERLING_RAW_REQUEST_JSON para la transacción " + transactionId + "\"");
        }
        return construirResultado(remision, resend, json, null, null);
    }

    private ApvResult enviarRemision(ApvResult resuelto) {
        if (resuelto.getJson() == null) return resuelto;
        String response = reprocesoService.enviar(resuelto.getJson(), resuelto.getRemision()).getResponse();
        if (esError(response)) resuelto.setError(response);
        else resuelto.setFachada(response);
        return resuelto;
    }

    // Solo el error de fachada se reintenta: sin JSON no hay nada que reenviar.
    static boolean esReintentable(ApvResult resultado) {
        return esError(resultado.getError()) && resultado.getJson() != null;
    }

    private String reenviarASterling(List<String> remisiones) {
        log.info("Enviando resend a Sterling con {} remisiones", remisiones.size());
        // El TXT viaja en memoria: en el cable es igual a subir el archivo y no hay temporal que limpiar.
        MultipartBodyBuilder cuerpo = new MultipartBodyBuilder();
        cuerpo.part("fileRemissions", new ByteArrayResource(String.join("\n", remisiones).getBytes(StandardCharsets.UTF_8)))
                .filename("remisiones.txt")
                .contentType(MediaType.TEXT_PLAIN);
        try {
            String response = webClient.post()
                    .uri("/resendSterlingRemissions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(cuerpo.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response == null ? "" : response;
        } catch (WebClientResponseException e) {
            log.error("Error HTTP en el resend a Sterling: {} - {}", e.getMessage(), e.getResponseBodyAsString());
            return "\"error\": \"" + e.getMessage() + "\", \"body\": " + e.getResponseBodyAsString();
        } catch (Exception e) {
            log.error("Error en el resend a Sterling: {}", e.getMessage());
            return "\"error\": \"" + e.getMessage() + "\"";
        }
    }

    public EstatusFulfillment obtenerEstatus(String jobId) {
        log.info("Entrando a obtenerEstatus de APV para job {}", jobId);
        EstatusFulfillment estatus = estatusPorJob.get(jobId);
        if (estatus == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        log.info("Finalizando obtenerEstatus de APV para job {}", jobId);
        return estatus;
    }

    public List<EstatusFulfillment> obtenerJobs() {
        log.info("Entrando a obtenerJobs de APV");
        List<EstatusFulfillment> jobs = estatusPorJob.values().stream()
                .sorted(Comparator.comparing(EstatusFulfillment::getInicio).reversed())
                .toList();
        log.info("Finalizando obtenerJobs de APV con {} jobs", jobs.size());
        return jobs;
    }

    public List<ApvResult> obtenerResultados(String jobId) {
        log.info("Entrando a obtenerResultados de APV para job {}", jobId);
        List<ApvResult> resultados = resultadosPorJob.get(jobId);
        if (resultados == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        List<ApvResult> copia = List.copyOf(resultados);
        log.info("Finalizando obtenerResultados de APV para job {} con {} resultados", jobId, copia.size());
        return copia;
    }

    static boolean esError(String texto) {
        return texto != null && texto.startsWith("\"error\"");
    }

    private static ApvResult construirResultado(String remision, String resend, String json, String fachada, String error) {
        return ApvResult.builder()
                .remision(remision)
                .resend(resend)
                .json(json)
                .fachada(fachada)
                .error(error)
                .build();
    }

    private void pausar() {
        if (pausaErrorMs <= 0) return;
        try {
            Thread.sleep(pausaErrorMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Pausa interrumpida: {}", e.getMessage());
        }
    }

    public int liberarMemoria() {
        log.info("Entrando a liberarMemoria");
        int jobs = estatusPorJob.size();
        estatusPorJob.clear();
        resultadosPorJob.clear();
        log.info("Finalizando liberarMemoria con {} jobs liberados", jobs);
        return jobs;
    }

    // Tras /memoria/liberar el job ya no existe: se corta el hilo en vez de volver a escribirlo en memoria.
    private void verificarVigente(String jobId) {
        if (!resultadosPorJob.containsKey(jobId)) throw new CancellationException("Job " + jobId + " liberado de memoria");
    }

    private void publicarEstatus(String jobId, String estatus, int total, int procesados, int conError,
                                 int reprocesados, String remisionActual, LocalDateTime inicio, LocalDateTime fin) {
        verificarVigente(jobId);
        estatusPorJob.put(jobId, EstatusFulfillment.builder()
                .jobId(jobId)
                .estatus(estatus)
                .totalTrackings(total)
                .procesados(procesados)
                .conErrorGateway(conError)
                .reprocesados(reprocesados)
                .trackingActual(remisionActual)
                .inicio(inicio)
                .fin(fin)
                .build());
    }

    private record Pendiente(int posicion, String remision, int ronda) {
    }
}
