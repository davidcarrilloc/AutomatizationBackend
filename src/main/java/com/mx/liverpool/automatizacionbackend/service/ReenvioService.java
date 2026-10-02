package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.liverpool.automatizacionbackend.model.FulfillmentResult;
import com.mx.liverpool.automatizacionbackend.model.ReenvioBitacora;
import com.mx.liverpool.automatizacionbackend.model.ReenvioResultado;
import com.mx.liverpool.automatizacionbackend.payload.response.EstatusReenvio;
import com.mx.liverpool.automatizacionbackend.repository.ReenvioRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ReenvioService {
    private static final String ESTATUS_SIN_INICIAR = "SIN_INICIAR";
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_DETENIENDO = "DETENIENDO";
    private static final String ESTATUS_DETENIDO = "DETENIDO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_ERROR = "ERROR";
    protected static final String CATEGORIA_SUCCESS = "SUCCESS";
    protected static final String CATEGORIA_FAILURE = "FAILURE";
    protected static final String CATEGORIA_ERROR = "ERROR";
    protected static final String CATEGORIA_OTRO = "OTRO";
    protected static final String LIMPIAR_DIAS = "DIAS";
    protected static final String LIMPIAR_TODO = "TODO";
    private static final List<String> CATEGORIAS = List.of(CATEGORIA_SUCCESS, CATEGORIA_FAILURE, CATEGORIA_ERROR, CATEGORIA_OTRO);
    private static final long PAUSA_GATEWAY_MS = 3_000L;
    private static final int MAX_RONDAS_REPROCESO = 3;
    private static final long MAX_FILAS_EXCEL = 1_048_575L;
    private static final String FECHA_MINIMA = "0000-01-01";
    private static final String FECHA_MAXIMA = "9999-12-31";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ReenvioRepository reenvioRepository;
    private final FulfillmentService fulfillmentService;
    private final ReenvioService self;
    private final LocalDate fechaLimite;
    private volatile EstatusReenvio estatus = EstatusReenvio.builder().estatus(ESTATUS_SIN_INICIAR).build();
    private volatile boolean detener;
    private volatile CompletableFuture<Void> corrida = CompletableFuture.completedFuture(null);
    // Hilo de la corrida vigente: tras /memoria/liberar el hilo anterior queda huérfano y se corta solo.
    private volatile Thread hiloCorrida;

    @Autowired
    public ReenvioService(ReenvioRepository reenvioRepository,
                          FulfillmentService fulfillmentService,
                          @Lazy ReenvioService self,
                          @Value("${reenvio.fecha-limite}") String fechaLimite) {
        this.reenvioRepository = reenvioRepository;
        this.fulfillmentService = fulfillmentService;
        this.self = self;
        this.fechaLimite = LocalDate.parse(fechaLimite);
    }

    public synchronized EstatusReenvio ejecutar() {
        log.info("Entrando a ejecutar");
        EstatusReenvio resultado;
        if (!corrida.isDone()) {
            detener = true;
            estatus.setEstatus(ESTATUS_DETENIENDO);
            resultado = estatus;
        } else {
            resultado = iniciar();
        }
        log.info("Finalizando ejecutar con estatus {}", resultado.getEstatus());
        return conConteos(resultado);
    }

    public synchronized EstatusReenvio reiniciar() {
        log.info("Entrando a reiniciar");
        detener = true;
        corrida.join();
        EstatusReenvio resultado = iniciar();
        log.info("Finalizando reiniciar con estatus {}", resultado.getEstatus());
        return conConteos(resultado);
    }

    // Sin synchronized: ejecutar/reiniciar/limpiar pueden estar bloqueados en corrida.join() de un hilo colgado.
    public EstatusReenvio liberarMemoria() {
        log.info("Entrando a liberarMemoria");
        detener = true;
        hiloCorrida = null;
        corrida.complete(null);
        estatus = EstatusReenvio.builder().estatus(ESTATUS_SIN_INICIAR).build();
        log.info("Finalizando liberarMemoria");
        return estatus;
    }

    public EstatusReenvio obtenerEstatus() {
        log.info("Entrando a obtenerEstatus");
        EstatusReenvio resultado = conConteos(estatus);
        log.info("Finalizando obtenerEstatus");
        return resultado;
    }

    public synchronized EstatusReenvio limpiar(List<String> tipos) {
        log.info("Entrando a limpiar con {}", tipos);
        Set<String> seleccion = validarTipos(tipos);
        detener = true;
        corrida.join();

        boolean todo = seleccion.contains(LIMPIAR_TODO);
        List<String> categorias = todo ? CATEGORIAS : CATEGORIAS.stream().filter(seleccion::contains).toList();
        int resultados = categorias.isEmpty() ? 0 : reenvioRepository.borrarResultados(categorias);
        int dias = todo || seleccion.contains(LIMPIAR_DIAS) ? reenvioRepository.borrarBitacora() : 0;

        if (todo) estatus = EstatusReenvio.builder().estatus(ESTATUS_SIN_INICIAR).build();
        estatus.setMensaje("Se borraron " + resultados + " resultados " + categorias + " y " + dias + " días");
        log.info("Finalizando limpiar: {} resultados {} y {} días", resultados, categorias, dias);
        return conConteos(estatus);
    }

    public List<ReenvioBitacora> obtenerBitacora() {
        log.info("Entrando a obtenerBitacora");
        List<ReenvioBitacora> bitacora = reenvioRepository.obtenerBitacora();
        log.info("Finalizando obtenerBitacora con {} días", bitacora.size());
        return bitacora;
    }

    public List<ReenvioResultado> obtenerResultados(LocalDate fechaInicio, LocalDate fechaFin) {
        log.info("Entrando a obtenerResultados con el rango {} - {}", fechaInicio, fechaFin);
        String inicio = fechaInicio == null ? FECHA_MINIMA : fechaInicio.toString();
        String fin = fechaFin == null ? FECHA_MAXIMA : fechaFin.toString();
        long conteo = reenvioRepository.obtenerConteoResultados(inicio, fin);
        if (conteo > MAX_FILAS_EXCEL) {
            throw new IllegalArgumentException("El rango tiene " + conteo + " filas y Excel admite " + MAX_FILAS_EXCEL
                    + ". Reduce el rango de fechas.");
        }
        List<ReenvioResultado> resultados = reenvioRepository.obtenerResultados(inicio, fin);
        log.info("Finalizando obtenerResultados con {} resultados", resultados.size());
        return resultados;
    }

    @Async("reenvioExecutor")
    public CompletableFuture<Void> procesarCorrida(List<LocalDate> pendientes, int diasTotales) {
        log.info("Entrando a procesarCorrida con {} días pendientes", pendientes.size());
        hiloCorrida = Thread.currentThread();
        LocalDateTime inicio = LocalDateTime.now();
        int diasProcesados = diasTotales - pendientes.size();
        try {
            for (LocalDate fecha : pendientes) {
                if (detener || !esVigente() || !procesarDia(fecha, inicio, diasProcesados, diasTotales)) break;
                diasProcesados++;
            }
            if (!esVigente()) {
                log.info("Finalizando procesarCorrida: hilo liberado de memoria");
                return CompletableFuture.completedFuture(null);
            }
            estatus = EstatusReenvio.builder()
                    .estatus(diasProcesados == diasTotales ? ESTATUS_COMPLETADO : ESTATUS_DETENIDO)
                    .diasProcesados(diasProcesados)
                    .diasTotales(diasTotales)
                    .inicio(inicio)
                    .fin(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Error en procesarCorrida: {}", e.getMessage(), e);
            if (!esVigente()) return CompletableFuture.completedFuture(null);
            estatus = EstatusReenvio.builder()
                    .estatus(ESTATUS_ERROR)
                    .fechaActual(estatus.getFechaActual())
                    .diasProcesados(diasProcesados)
                    .diasTotales(diasTotales)
                    .inicio(inicio)
                    .fin(LocalDateTime.now())
                    .mensaje(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                    .build();
        }
        log.info("Finalizando procesarCorrida con estatus {} y {} de {} días", estatus.getEstatus(), diasProcesados, diasTotales);
        return CompletableFuture.completedFuture(null);
    }

    private EstatusReenvio iniciar() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        Set<String> procesados = reenvioRepository.obtenerBitacora().stream()
                .map(ReenvioBitacora::getFecha)
                .collect(Collectors.toSet());
        List<LocalDate> pendientes = diasPendientes(ayer, fechaLimite, procesados);
        int diasTotales = (int) Math.max(0, ChronoUnit.DAYS.between(fechaLimite, ayer) + 1);
        log.info("Días pendientes {} de {}", pendientes.size(), diasTotales);

        if (pendientes.isEmpty()) {
            estatus = EstatusReenvio.builder()
                    .estatus(ESTATUS_COMPLETADO)
                    .diasProcesados(diasTotales)
                    .diasTotales(diasTotales)
                    .mensaje("No hay días pendientes entre " + ayer + " y " + fechaLimite)
                    .build();
            return estatus;
        }

        detener = false;
        estatus = EstatusReenvio.builder()
                .estatus(ESTATUS_EN_PROCESO)
                .fechaActual(pendientes.getFirst().toString())
                .diasProcesados(diasTotales - pendientes.size())
                .diasTotales(diasTotales)
                .inicio(LocalDateTime.now())
                .build();
        EstatusReenvio inicial = estatus;
        corrida = self.procesarCorrida(pendientes, diasTotales);
        return inicial;
    }

    private boolean procesarDia(LocalDate fecha, LocalDateTime inicio, int diasProcesados, int diasTotales) {
        String dia = fecha.toString();
        LocalDateTime inicioDia = LocalDateTime.now();
        List<ReenvioResultado> remisiones = reenvioRepository.obtenerRemisionesDia(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay())
                .stream()
                .filter(r -> r.getRemision() != null && !r.getRemision().isBlank())
                .peek(r -> r.setRemision(fulfillmentService.rellenarDiezDigitos(r.getRemision())))
                .distinct()
                .toList();
        List<ReenvioResultado> hechos = reenvioRepository.obtenerProcesadasDia(dia);
        Set<String> llavesHechas = hechos.stream().map(ReenvioService::llave).collect(Collectors.toSet());
        log.info("Día {}: {} remisiones, {} ya registradas", dia, remisiones.size(), hechos.size());

        Map<String, Integer> conteo = new HashMap<>();
        hechos.forEach(h -> conteo.merge(h.getCategoria(), 1, Integer::sum));
        List<ReenvioResultado> cola = remisiones.stream().filter(r -> !llavesHechas.contains(llave(r))).toList();
        int procesadas = remisiones.size() - cola.size();
        for (int ronda = 0; ronda <= MAX_RONDAS_REPROCESO && !cola.isEmpty(); ronda++) {
            if (ronda > 0) log.info("Día {}: ronda {} con {} diferidos", dia, ronda, cola.size());
            List<ReenvioResultado> diferidos = new ArrayList<>();
            for (ReenvioResultado remision : cola) {
                if (detener || !esVigente()) return false;
                publicarAvance(dia, diasProcesados, diasTotales, remisiones.size(), procesadas, remision.getRemision(), inicio);
                FulfillmentResult resultado = fulfillmentService.consultarFulfillment(remision.getRemision()).block();
                if (!esVigente()) return false;
                if (fulfillmentService.esErrorGateway(resultado) && ronda < MAX_RONDAS_REPROCESO) {
                    log.warn("Remisión {} con error de gateway, se difiere al final del día tras pausa de {} ms", remision.getRemision(), PAUSA_GATEWAY_MS);
                    pausar();
                    diferidos.add(remision);
                    continue;
                }
                String categoria = clasificar(resultado);
                reenvioRepository.insertarResultado(ReenvioResultado.builder()
                        .fecha(dia)
                        .remision(remision.getRemision())
                        .origen(remision.getOrigen())
                        .categoria(categoria)
                        .response(resultado == null ? null : resultado.getResponse())
                        .json(resultado == null ? null : resultado.getJson())
                        .build());
                conteo.merge(categoria, 1, Integer::sum);
                procesadas++;
            }
            cola = diferidos;
        }

        reenvioRepository.insertarBitacora(ReenvioBitacora.builder()
                .fecha(dia)
                .total(remisiones.size())
                .success(conteo.getOrDefault(CATEGORIA_SUCCESS, 0))
                .failure(conteo.getOrDefault(CATEGORIA_FAILURE, 0))
                .error(conteo.getOrDefault(CATEGORIA_ERROR, 0))
                .otro(conteo.getOrDefault(CATEGORIA_OTRO, 0))
                .inicio(inicioDia.toString())
                .fin(LocalDateTime.now().toString())
                .build());
        log.info("Día {} terminado: {}", dia, conteo);
        return true;
    }

    private boolean esVigente() {
        return Thread.currentThread() == hiloCorrida;
    }

    protected static List<LocalDate> diasPendientes(LocalDate desde, LocalDate limite, Set<String> procesados) {
        if (desde.isBefore(limite)) return List.of();
        return desde.datesUntil(limite.minusDays(1), Period.ofDays(-1))
                .filter(fecha -> !procesados.contains(fecha.toString()))
                .toList();
    }

    protected static String clasificar(FulfillmentResult resultado) {
        if (resultado == null) return CATEGORIA_ERROR;
        String response = resultado.getResponse() == null ? "" : resultado.getResponse();
        if (response.contains("\"error\"")) return CATEGORIA_ERROR;
        try {
            JsonNode primero = JSON.readTree(resultado.getJson()).path(0);
            if (CATEGORIA_FAILURE.equalsIgnoreCase(primero.path("statusMkp").asText())
                    || CATEGORIA_FAILURE.equalsIgnoreCase(primero.path("statusOms").asText())) {
                return CATEGORIA_FAILURE;
            }
        } catch (Exception e) {
            return CATEGORIA_ERROR;
        }
        return response.contains(CATEGORIA_SUCCESS) ? CATEGORIA_SUCCESS : CATEGORIA_OTRO;
    }

    private void publicarAvance(String dia, int diasProcesados, int diasTotales, int totalDia, int procesadasDia,
                                String remisionActual, LocalDateTime inicio) {
        estatus = EstatusReenvio.builder()
                .estatus(detener ? ESTATUS_DETENIENDO : ESTATUS_EN_PROCESO)
                .fechaActual(dia)
                .diasProcesados(diasProcesados)
                .diasTotales(diasTotales)
                .totalDia(totalDia)
                .procesadasDia(procesadasDia)
                .remisionActual(remisionActual)
                .inicio(inicio)
                .build();
    }

    // Los conteos salen de reenvio_resultado, no de memoria: sobreviven pausas, reinicios de la app y reflejan /limpiar.
    private EstatusReenvio conConteos(EstatusReenvio actual) {
        Map<String, Integer> conteo = reenvioRepository.obtenerConteoCategorias();
        actual.setSuccess(conteo.getOrDefault(CATEGORIA_SUCCESS, 0));
        actual.setFailure(conteo.getOrDefault(CATEGORIA_FAILURE, 0));
        actual.setError(conteo.getOrDefault(CATEGORIA_ERROR, 0));
        actual.setOtro(conteo.getOrDefault(CATEGORIA_OTRO, 0));
        return actual;
    }

    protected static Set<String> validarTipos(List<String> tipos) {
        if (tipos == null || tipos.isEmpty()) {
            throw new IllegalArgumentException("Indica qué limpiar: SUCCESS, FAILURE, ERROR, OTRO, DIAS o TODO");
        }
        Set<String> seleccion = tipos.stream().map(t -> t.trim().toUpperCase()).collect(Collectors.toSet());
        seleccion.stream()
                .filter(t -> !CATEGORIAS.contains(t) && !LIMPIAR_DIAS.equals(t) && !LIMPIAR_TODO.equals(t))
                .findFirst()
                .ifPresent(t -> {
                    throw new IllegalArgumentException("Tipo inválido: " + t + ". Usa SUCCESS, FAILURE, ERROR, OTRO, DIAS o TODO");
                });
        return seleccion;
    }

    private static String llave(ReenvioResultado resultado) {
        return resultado.getRemision() + "|" + resultado.getOrigen();
    }

    private void pausar() {
        try {
            Thread.sleep(PAUSA_GATEWAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Pausa interrumpida: {}", e.getMessage());
        }
    }
}
