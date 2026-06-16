package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.CorreoTx;
import com.mx.liverpool.automatizacionbackend.model.EstatusProcesamientoCorreos;
import com.mx.liverpool.automatizacionbackend.repository.CorreoTxRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Log4j2
public class CorreoTxService {
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";

    private final ExcelService excelService;
    private final CorreoTxRepository correoTxRepository;
    private final CorreoTxService self;
    private final Path carpetaSalida;
    private final Map<String, EstatusProcesamientoCorreos> estatusPorJob = new ConcurrentHashMap<>();

    @Autowired
    public CorreoTxService(ExcelService excelService,
                           CorreoTxRepository correoTxRepository,
                           @Lazy CorreoTxService self,
                           @Value("${correos.tx.output.path}") String rutaSalida) {
        this.excelService = excelService;
        this.correoTxRepository = correoTxRepository;
        this.self = self;
        this.carpetaSalida = Paths.get(rutaSalida);
    }

    public EstatusProcesamientoCorreos iniciarProcesamiento(LinkedHashMap<String, List<String>> segmentos) {
        log.info("Entrando a iniciarProcesamiento con {} segmentos", segmentos.size());

        String jobId = String.valueOf(System.currentTimeMillis());
        publicarEstatus(jobId, ESTATUS_EN_PROCESO, segmentos.size(), 0, 0, 0L, null, new ArrayList<>(), LocalDateTime.now(), null);

        self.procesarSegmentos(jobId, segmentos);

        log.info("Finalizando iniciarProcesamiento, job {} encolado", jobId);
        return estatusPorJob.get(jobId);
    }

    @Async("correosTxExecutor")
    public void procesarSegmentos(String jobId, LinkedHashMap<String, List<String>> segmentos) {
        log.info("Entrando a procesarSegmentos para job {}", jobId);

        int total = segmentos.size();
        int procesados = 0;
        int omitidos = 0;
        long totalRegistros = 0;
        List<String> errores = new ArrayList<>();
        LocalDateTime inicio = estatusPorJob.get(jobId).getInicio();

        try {
            Files.createDirectories(carpetaSalida);
        } catch (IOException e) {
            log.error("No se pudo crear la carpeta de salida {}: {}", carpetaSalida, e.getMessage());
            errores.add("No se pudo crear la carpeta de salida: " + e.getMessage());
            publicarEstatus(jobId, ESTATUS_COMPLETADO_CON_ERRORES, total, procesados, omitidos, totalRegistros, null, errores, inicio, LocalDateTime.now());
            return;
        }

        for (Map.Entry<String, List<String>> segmento : segmentos.entrySet()) {
            String nombre = segmento.getKey();
            Path destino = carpetaSalida.resolve("Reporte_" + nombre + ".xlsx");

            publicarEstatus(jobId, ESTATUS_EN_PROCESO, total, procesados, omitidos, totalRegistros, nombre, errores, inicio, null);

            if (Files.exists(destino)) {
                log.info("Segmento {} ya procesado, se omite (reanudación)", nombre);
                omitidos++;
                continue;
            }

            try {
                totalRegistros += procesarSegmento(nombre, segmento.getValue(), destino);
                procesados++;
            } catch (Exception e) {
                log.error("Error procesando segmento {}: {}", nombre, e.getMessage(), e);
                errores.add(nombre + ": " + e.getMessage());
            }
        }

        String estatusFinal = errores.isEmpty() ? ESTATUS_COMPLETADO : ESTATUS_COMPLETADO_CON_ERRORES;
        publicarEstatus(jobId, estatusFinal, total, procesados, omitidos, totalRegistros, null, errores, inicio, LocalDateTime.now());
        log.info("Finalizando procesarSegmentos para job {}: {} procesados, {} omitidos, {} errores",
                jobId, procesados, omitidos, errores.size());
    }

    private long procesarSegmento(String nombre, List<String> correos, Path destino) throws IOException {
        log.info("Consultando segmento {} con {} correos", nombre, correos.size());
        List<CorreoTx> resultados = correoTxRepository.obtenerTxPorCorreos(correos);

        Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
        excelService.escribirReporteCorreosTx(resultados, temporal);
        Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        log.info("Segmento {} escrito con {} registros", nombre, resultados.size());
        return resultados.size();
    }

    public EstatusProcesamientoCorreos obtenerEstatus(String jobId) {
        log.info("Entrando a obtenerEstatus para job {}", jobId);
        EstatusProcesamientoCorreos estatus = estatusPorJob.get(jobId);
        if (estatus == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        log.info("Finalizando obtenerEstatus para job {}", jobId);
        return estatus;
    }

    private void publicarEstatus(String jobId, String estatus, int total, int procesados, int omitidos,
                                 long totalRegistros, String segmentoActual, List<String> errores,
                                 LocalDateTime inicio, LocalDateTime fin) {
        estatusPorJob.put(jobId, EstatusProcesamientoCorreos.builder()
                .jobId(jobId)
                .estatus(estatus)
                .carpetaSalida(carpetaSalida.toString())
                .totalSegmentos(total)
                .segmentosProcesados(procesados)
                .segmentosOmitidos(omitidos)
                .totalRegistros(totalRegistros)
                .segmentoActual(segmentoActual)
                .errores(List.copyOf(errores))
                .inicio(inicio)
                .fin(fin)
                .build());
    }
}
