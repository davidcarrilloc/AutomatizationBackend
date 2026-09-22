package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.repository.ExtractoBcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class ExtractoBcService {

    private static final DateTimeFormatter FORMATO_NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExtractoBcRepository extractoBcRepository;
    private final SftpService sftpService;

    public Map<String, Object> generarDiaAnterior() {
        LocalDateTime inicio = inicioDiaAnterior(LocalDateTime.now());
        return generarYEnviar(inicio, inicio.plusDays(1).minusSeconds(1));
    }

    protected static LocalDateTime inicioDiaAnterior(LocalDateTime referencia) {
        return referencia.minusDays(1).truncatedTo(ChronoUnit.DAYS);
    }

    public Map<String, Object> generarYEnviar(LocalDateTime inicio, LocalDateTime fin) {
        log.info("Entrando a generarYEnviar con la ventana {} - {}", inicio, fin);
        String nombreArchivo = "liverpool_sl_" + FORMATO_NOMBRE.format(inicio) + ".csv";

        Map<String, Object> resumen = new LinkedHashMap<>();
        try {
            List<String> remisiones = extractoBcRepository.obtenerRemisionesLiverpoolSl(inicio, fin);
            sftpService.enviarArchivo(nombreArchivo, construirCsv(remisiones));
            resumen.put(nombreArchivo, remisiones.size());
        } catch (Exception e) {
            log.error("Error en el extracto {}: {}", nombreArchivo, e.getMessage(), e);
            resumen.put(nombreArchivo, "Error: " + e.getMessage());
        }

        log.info("Finalizando generarYEnviar: {}", resumen);
        return resumen;
    }

    protected byte[] construirCsv(List<String> valores) {
        String contenido = valores.isEmpty() ? "" : String.join("\n", valores) + "\n";
        return contenido.getBytes(StandardCharsets.UTF_8);
    }
}
