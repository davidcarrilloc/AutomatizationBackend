package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.EstatusFulfillment;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoCompletoService.FilaPreparada;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Variante asíncrona (jobId) del reproceso completo. Una sola pasada, sin pausas largas entre envíos;
 * la fila con error de gateway (500/504) se difiere al final y se reintenta hasta max-rondas. Reusa la
 * transformación de {@link ReprocesoCompletoService} y el envío de {@link ReprocesoService}.
 */
@Service
@Log4j2
public class ReprocesoCompletoAsyncService {
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";

    private final ReprocesoCompletoService reprocesoCompletoService;
    private final ReprocesoService reprocesoService;
    private final ReprocesoCompletoAsyncService self;
    private final long pausaEnvioMs;
    private final long pausaGatewayMs;
    private final int maxRondas;
    private final Map<String, EstatusFulfillment> estatusPorJob = new ConcurrentHashMap<>();
    private final Map<String, List<ReprocesoResult>> resultadosPorJob = new ConcurrentHashMap<>();

    @Autowired
    public ReprocesoCompletoAsyncService(ReprocesoCompletoService reprocesoCompletoService,
                                         ReprocesoService reprocesoService,
                                         @Lazy ReprocesoCompletoAsyncService self,
                                         @Value("${reproceso.completo.pausa-envio-ms}") long pausaEnvioMs,
                                         @Value("${reproceso.completo.pausa-gateway-ms}") long pausaGatewayMs,
                                         @Value("${reproceso.completo.max-rondas}") int maxRondas) {
        this.reprocesoCompletoService = reprocesoCompletoService;
        this.reprocesoService = reprocesoService;
        this.self = self;
        this.pausaEnvioMs = pausaEnvioMs;
        this.pausaGatewayMs = pausaGatewayMs;
        this.maxRondas = maxRondas;
    }

    public EstatusFulfillment iniciarReproceso(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a iniciarReproceso de reproceso completo async con {} filas", filas.size());

        String jobId = String.valueOf(System.currentTimeMillis());
        resultadosPorJob.put(jobId, new ArrayList<>());
        publicarEstatus(jobId, ESTATUS_EN_PROCESO, filas.size(), 0, 0, 0, null, LocalDateTime.now(), null);

        self.procesarJob(jobId, filas);

        log.info("Finalizando iniciarReproceso de reproceso completo async, job {} encolado", jobId);
        return estatusPorJob.get(jobId);
    }

    @Async("fulfillmentExecutor")
    public void procesarJob(String jobId, List<ReprocesoNodeRow> filas) {
        log.info("Entrando a procesarJob de reproceso completo async para job {} con {} filas", jobId, filas.size());

        int total = filas.size();
        LocalDateTime inicio = estatusPorJob.get(jobId).getInicio();

        // Batch de BRIDGECORE + transformación fila por fila (sin enviar). Es parte del trabajo del job.
        List<FilaPreparada> preparadas = reprocesoCompletoService.prepararFilas(filas);

        AtomicInteger procesados = new AtomicInteger();
        AtomicInteger reprocesados = new AtomicInteger();
        AtomicInteger conErrorGateway = new AtomicInteger();

        List<ReprocesoResult> resultados = procesarConRondas(
                preparadas,
                fp -> reprocesoCompletoService.aplicarMarca(fp.nota(),
                        reprocesoService.enviar(fp.preparado().requestOriginal(), fp.fila().getTrackingNumber())),
                this::esErrorGateway,
                maxRondas,
                procesados, reprocesados, conErrorGateway,
                () -> pausar(pausaEnvioMs),
                () -> pausar(pausaGatewayMs),
                tracking -> publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados.get(),
                        conErrorGateway.get(), reprocesados.get(), tracking, inicio, null));

        resultadosPorJob.put(jobId, resultados);
        String estatusFinal = conErrorGateway.get() == 0 ? ESTATUS_COMPLETADO : ESTATUS_COMPLETADO_CON_ERRORES;
        publicarEstatus(jobId, estatusFinal, total, procesados.get(), conErrorGateway.get(), reprocesados.get(), null, inicio, LocalDateTime.now());
        log.info("Finalizando procesarJob de reproceso completo async para job {}: {} procesados, {} reprocesados, {} con error de gateway",
                jobId, procesados.get(), reprocesados.get(), conErrorGateway.get());
    }

    /**
     * Pasada única sobre las filas preparadas, conservando el orden de entrada. La fila omitida (no debe
     * enviarse) se resuelve sin llamar a la fachada; la enviada con error de gateway se difiere al final
     * y se reintenta hasta maxRondas (en la última se conserva el error). Estático y sin estado para poder
     * probarse sin arrancar la app.
     */
    static List<ReprocesoResult> procesarConRondas(List<FilaPreparada> preparadas,
                                                   Function<FilaPreparada, ReprocesoResult> enviar,
                                                   Predicate<ReprocesoResult> esErrorGateway,
                                                   int maxRondas,
                                                   AtomicInteger procesados, AtomicInteger reprocesados, AtomicInteger conErrorGateway,
                                                   Runnable pausaEnvio, Runnable pausaGateway,
                                                   Consumer<String> publicarAvance) {
        ReprocesoResult[] porPosicion = new ReprocesoResult[preparadas.size()];
        Deque<Pendiente> diferidos = new ArrayDeque<>();

        for (int i = 0; i < preparadas.size(); i++) {
            FilaPreparada fp = preparadas.get(i);
            publicarAvance.accept(fp.fila().getTrackingNumber());
            if (!fp.preparado().debeEnviar()) {
                porPosicion[i] = ReprocesoResult.builder()
                        .requestOriginal(fp.preparado().requestOriginal())
                        .trackingNumber(fp.fila().getTrackingNumber())
                        .response(fp.preparado().responseSiNoEnvia())
                        .build();
                procesados.incrementAndGet();
                continue;
            }
            ReprocesoResult resultado = enviar.apply(fp);
            if (esErrorGateway.test(resultado)) {
                pausaGateway.run();
                diferidos.add(new Pendiente(i, fp, 1));
            } else {
                porPosicion[i] = resultado;
                procesados.incrementAndGet();
                pausaEnvio.run();
            }
        }

        while (!diferidos.isEmpty()) {
            Pendiente pendiente = diferidos.poll();
            publicarAvance.accept(pendiente.fp().fila().getTrackingNumber());
            ReprocesoResult resultado = enviar.apply(pendiente.fp());
            if (esErrorGateway.test(resultado) && pendiente.ronda() < maxRondas) {
                pausaGateway.run();
                diferidos.add(new Pendiente(pendiente.posicion(), pendiente.fp(), pendiente.ronda() + 1));
            } else {
                porPosicion[pendiente.posicion()] = resultado;
                procesados.incrementAndGet();
                if (esErrorGateway.test(resultado)) conErrorGateway.incrementAndGet();
                else reprocesados.incrementAndGet();
            }
        }

        return new ArrayList<>(Arrays.asList(porPosicion));
    }

    public EstatusFulfillment obtenerEstatus(String jobId) {
        log.info("Entrando a obtenerEstatus de reproceso completo async para job {}", jobId);
        EstatusFulfillment estatus = estatusPorJob.get(jobId);
        if (estatus == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        log.info("Finalizando obtenerEstatus de reproceso completo async para job {}", jobId);
        return estatus;
    }

    public List<EstatusFulfillment> obtenerJobs() {
        log.info("Entrando a obtenerJobs de reproceso completo async");
        List<EstatusFulfillment> jobs = estatusPorJob.values().stream()
                .sorted(Comparator.comparing(EstatusFulfillment::getInicio).reversed())
                .toList();
        log.info("Finalizando obtenerJobs de reproceso completo async con {} jobs", jobs.size());
        return jobs;
    }

    public List<ReprocesoResult> obtenerResultados(String jobId) {
        log.info("Entrando a obtenerResultados de reproceso completo async para job {}", jobId);
        List<ReprocesoResult> resultados = resultadosPorJob.get(jobId);
        if (resultados == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        List<ReprocesoResult> copia = List.copyOf(resultados);
        log.info("Finalizando obtenerResultados de reproceso completo async para job {} con {} resultados", jobId, copia.size());
        return copia;
    }

    /** Solo el error de gateway (500/504) se difiere y reintenta; un 400, un JSON inválido o una omisión no. */
    private boolean esErrorGateway(ReprocesoResult resultado) {
        if (resultado == null || resultado.getResponse() == null) return false;
        return resultado.getResponse().contains("500 Internal Server Error")
                || resultado.getResponse().contains("504 Gateway Timeout");
    }

    private void pausar(long milisegundos) {
        if (milisegundos <= 0) return;
        try {
            Thread.sleep(milisegundos);
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

    private record Pendiente(int posicion, FilaPreparada fp, int ronda) {
    }
}
