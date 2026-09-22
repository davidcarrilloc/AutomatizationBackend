package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.liverpool.automatizacionbackend.model.EstatusFachada;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import io.netty.channel.ChannelOption;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Log4j2
public class FachadaService {
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final FachadaService self;
    private final int tamanoBloque;
    private final int maxRondas;
    // W = bloques que corren en paralelo; el pool de conexiones se dimensiona al mismo tamaño.
    private final int w;
    private final Map<String, EstatusFachada> estatusPorJob = new ConcurrentHashMap<>();
    private final Map<String, List<ReprocesoResult>> resultadosPorJob = new ConcurrentHashMap<>();

    @Autowired
    public FachadaService(WebClient.Builder webClientBuilder,
                          ObjectMapper objectMapper,
                          @Lazy FachadaService self,
                          @Value("${reproceso.i200.base-url}") String baseUrl,
                          @Value("${reproceso.i200.apikey}") String apikey,
                          @Value("${fachada.tamano-bloque}") int tamanoBloque,
                          @Value("${fachada.factor}") int factor,
                          @Value("${fachada.max-rondas}") int maxRondas) {
        this.tamanoBloque = tamanoBloque;
        this.maxRondas = maxRondas;
        this.w = Runtime.getRuntime().availableProcessors() * factor;

        // Pool de conexiones = W para que ningún bloque se quede esperando conexión; codec ampliado por si
        // la respuesta de la fachada rebasa el límite de 256 KB del codec por defecto.
        ConnectionProvider provider = ConnectionProvider.builder("fachada")
                .maxConnections(this.w)
                .pendingAcquireMaxCount(-1)
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000);
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("apikey", apikey)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;
        this.self = self;
    }

    public EstatusFachada iniciarReproceso(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a iniciarReproceso de fachada con {} filas", filas.size());

        int bloques = (int) Math.ceil((double) filas.size() / tamanoBloque);
        String jobId = String.valueOf(System.currentTimeMillis());
        resultadosPorJob.put(jobId, new ArrayList<>());
        publicarEstatus(jobId, ESTATUS_EN_PROCESO, filas.size(), 0, 0, bloques, LocalDateTime.now(), null);

        self.procesarJob(jobId, filas);

        log.info("Finalizando iniciarReproceso de fachada, job {} encolado ({} bloques, W={})", jobId, bloques, w);
        return estatusPorJob.get(jobId);
    }

    @Async("statusOmsExecutor")
    public void procesarJob(String jobId, List<ReprocesoNodeRow> filas) {
        log.info("Entrando a procesarJob de fachada para job {} con {} filas", jobId, filas.size());

        List<List<ReprocesoNodeRow>> bloques = particionar(filas, tamanoBloque);
        int total = filas.size();
        LocalDateTime inicio = estatusPorJob.get(jobId).getInicio();

        // Un slot por bloque: se juntan en orden de bloque para preservar el orden de entrada.
        List<List<ReprocesoResult>> porBloque = new ArrayList<>(java.util.Collections.nCopies(bloques.size(), null));
        AtomicInteger procesados = new AtomicInteger();
        AtomicInteger errores = new AtomicInteger();
        Semaphore permisos = new Semaphore(w);

        try (ExecutorService ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futuros = new ArrayList<>();
            for (int i = 0; i < bloques.size(); i++) {
                final int indice = i;
                final List<ReprocesoNodeRow> bloque = bloques.get(i);
                futuros.add(ejecutor.submit(() -> {
                    permisos.acquire();
                    try {
                        List<ReprocesoResult> resultados = procesarBloque(bloque, procesados, errores);
                        porBloque.set(indice, resultados);
                        // Vuelca los bloques ya listos, en orden, para permitir descarga parcial.
                        volcarResultados(jobId, porBloque);
                        publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados.get(), errores.get(), bloques.size(), inicio, null);
                    } finally {
                        permisos.release();
                    }
                    return null;
                }));
            }
            for (Future<?> futuro : futuros) {
                futuro.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("procesarJob de fachada interrumpido para job {}: {}", jobId, e.getMessage());
        } catch (Exception e) {
            log.error("Error en procesarJob de fachada para job {}: {}", jobId, e.getMessage());
        }

        volcarResultados(jobId, porBloque);
        String estatusFinal = errores.get() == 0 ? ESTATUS_COMPLETADO : ESTATUS_COMPLETADO_CON_ERRORES;
        publicarEstatus(jobId, estatusFinal, total, procesados.get(), errores.get(), bloques.size(), inicio, LocalDateTime.now());
        log.info("Finalizando procesarJob de fachada para job {}: {} procesados, {} con error", jobId, procesados.get(), errores.get());
    }

    private List<ReprocesoResult> procesarBloque(List<ReprocesoNodeRow> bloque, AtomicInteger procesados, AtomicInteger errores) {
        return procesarConReintentos(bloque, this::enviar, maxRondas, procesados, errores);
    }

    /**
     * Procesa un bloque fila por fila; la fila que da error se manda al final de la cola y se reintenta
     * hasta maxRondas (en la última ronda se conserva el error). El resultado conserva el orden del bloque.
     */
    static List<ReprocesoResult> procesarConReintentos(List<ReprocesoNodeRow> bloque,
                                                       java.util.function.Function<ReprocesoNodeRow, ReprocesoResult> enviar,
                                                       int maxRondas, AtomicInteger procesados, AtomicInteger errores) {
        Deque<Intento> cola = new ArrayDeque<>();
        for (int i = 0; i < bloque.size(); i++) {
            cola.add(new Intento(i, bloque.get(i), 0));
        }

        ReprocesoResult[] porPosicion = new ReprocesoResult[bloque.size()];
        while (!cola.isEmpty()) {
            Intento intento = cola.poll();
            ReprocesoResult resultado = enviar.apply(intento.fila);
            if (esError(resultado) && intento.ronda < maxRondas) {
                // Al final de la cola para reintentar tras el resto.
                cola.add(new Intento(intento.posicion, intento.fila, intento.ronda + 1));
            } else {
                porPosicion[intento.posicion] = resultado;
                if (esError(resultado)) errores.incrementAndGet();
                procesados.incrementAndGet();
            }
        }

        List<ReprocesoResult> resultados = new ArrayList<>();
        for (ReprocesoResult resultado : porPosicion) {
            resultados.add(resultado);
        }
        return resultados;
    }

    private ReprocesoResult enviar(ReprocesoNodeRow fila) {
        String json = fila.getJson();
        String tracking = fila.getTrackingNumber();
        // JSON inválido: se marca error y no se difiere (reintentar no lo va a arreglar).
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("JSON inválido para el tracking {}: {}", tracking, e.getMessage());
            return construirResultado(json, tracking, "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }

        log.info("Enviando a la fachada el tracking {}", tracking);
        try {
            String response = webClient.post()
                    .uri("/oms/sl/I200?origen=ecom")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(json)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return construirResultado(json, tracking, response == null ? "" : response);
        } catch (WebClientResponseException e) {
            log.error("Error HTTP enviando tracking {} a la fachada: {}", tracking, e.getMessage());
            return construirResultado(json, tracking,
                    "\"error\": \"" + e.getMessage() + "\", \"body\": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error enviando tracking {} a la fachada: {}", tracking, e.getMessage());
            return construirResultado(json, tracking, "\"error\": \"" + e.getMessage() + "\"");
        }
    }

    public EstatusFachada obtenerEstatus(String jobId) {
        log.info("Entrando a obtenerEstatus de fachada para job {}", jobId);
        EstatusFachada estatus = estatusPorJob.get(jobId);
        if (estatus == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        log.info("Finalizando obtenerEstatus de fachada para job {}", jobId);
        return estatus;
    }

    public List<EstatusFachada> obtenerJobs() {
        log.info("Entrando a obtenerJobs de fachada");
        List<EstatusFachada> jobs = estatusPorJob.values().stream()
                .sorted(Comparator.comparing(EstatusFachada::getInicio).reversed())
                .toList();
        log.info("Finalizando obtenerJobs de fachada con {} jobs", jobs.size());
        return jobs;
    }

    public List<ReprocesoResult> obtenerResultados(String jobId) {
        log.info("Entrando a obtenerResultados de fachada para job {}", jobId);
        List<ReprocesoResult> resultados = resultadosPorJob.get(jobId);
        if (resultados == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        List<ReprocesoResult> copia = List.copyOf(resultados);
        log.info("Finalizando obtenerResultados de fachada para job {} con {} resultados", jobId, copia.size());
        return copia;
    }

    /** Aplana los bloques ya terminados (en orden) hacia resultadosPorJob para permitir descarga parcial. */
    private void volcarResultados(String jobId, List<List<ReprocesoResult>> porBloque) {
        List<ReprocesoResult> aplanado = new ArrayList<>();
        for (List<ReprocesoResult> bloque : porBloque) {
            if (bloque != null) aplanado.addAll(bloque);
        }
        resultadosPorJob.put(jobId, aplanado);
    }

    static boolean esError(ReprocesoResult resultado) {
        return resultado.getResponse() != null && resultado.getResponse().startsWith("\"error\"");
    }

    private ReprocesoResult construirResultado(String requestOriginal, String trackingNumber, String response) {
        return ReprocesoResult.builder()
                .requestOriginal(requestOriginal)
                .trackingNumber(trackingNumber)
                .response(response == null ? "" : response)
                .build();
    }

    private void publicarEstatus(String jobId, String estatus, int total, int procesados, int errores,
                                 int bloques, LocalDateTime inicio, LocalDateTime fin) {
        estatusPorJob.put(jobId, EstatusFachada.builder()
                .jobId(jobId)
                .estatus(estatus)
                .total(total)
                .procesados(procesados)
                .errores(errores)
                .bloques(bloques)
                .inicio(inicio)
                .fin(fin)
                .build());
    }

    static List<List<ReprocesoNodeRow>> particionar(List<ReprocesoNodeRow> filas, int tamano) {
        List<List<ReprocesoNodeRow>> bloques = new ArrayList<>();
        for (int i = 0; i < filas.size(); i += tamano) {
            bloques.add(new ArrayList<>(filas.subList(i, Math.min(i + tamano, filas.size()))));
        }
        return bloques;
    }

    private record Intento(int posicion, ReprocesoNodeRow fila, int ronda) {
    }
}
