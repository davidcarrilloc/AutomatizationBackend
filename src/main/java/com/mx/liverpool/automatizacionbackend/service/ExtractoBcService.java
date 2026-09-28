package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.repository.ExtractoBcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Log4j2
public class ExtractoBcService {

    private static final DateTimeFormatter FORMATO_NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExtractoBcRepository extractoBcRepository;
    private final SftpService sftpService;

    private record Fuente(boolean ok, List<String> filas) {}

    public Map<String, Object> generarDiaAnterior() {
        LocalDateTime inicio = inicioDiaAnterior(LocalDateTime.now());
        return generarYEnviar(inicio, inicio.plusDays(1).minusSeconds(1));
    }

    protected static LocalDateTime inicioDiaAnterior(LocalDateTime referencia) {
        return referencia.minusDays(1).truncatedTo(ChronoUnit.DAYS);
    }

    public Map<String, Object> generarYEnviar(LocalDateTime inicio, LocalDateTime fin) {
        log.info("Entrando a generarYEnviar con la ventana {} - {}", inicio, fin);
        String sello = FORMATO_NOMBRE.format(inicio);

        Map<String, Object> resumen = new LinkedHashMap<>();

        Fuente sl = consultar("liverpool_sl", () -> extractoBcRepository.obtenerRemisionesLiverpoolSl(inicio, fin));
        Fuente sbb = consultar("suburbia", () -> extractoBcRepository.obtenerShipGroupsSuburbia(inicio, fin));
        subir("oms_sl_" + sello + ".csv", List.of(sl, sbb), resumen);

        Fuente bt = consultar("liverpool_bt", () -> extractoBcRepository.obtenerOrdenesVentaLiverpoolBt(inicio, fin));
        subir("oms_bt_" + sello + ".csv", List.of(bt), resumen);

        log.info("Finalizando generarYEnviar: {}", resumen);
        return resumen;
    }

    private Fuente consultar(String clave, Supplier<List<String>> consulta) {
        try {
            List<String> filas = consulta.get();
            log.info("Consulta {}: {} filas", clave, filas.size());
            return new Fuente(true, filas);
        } catch (Exception e) {
            log.error("Error en la consulta {}: {}", clave, e.getMessage(), e);
            return new Fuente(false, List.of());
        }
    }

    private void subir(String nombreArchivo, List<Fuente> fuentes, Map<String, Object> resumen) {
        if (fuentes.stream().noneMatch(Fuente::ok)) {
            resumen.put(nombreArchivo, "Omitido: todas las fuentes fallaron");
            return;
        }
        List<String> valores = new ArrayList<>();
        fuentes.forEach(fuente -> valores.addAll(fuente.filas()));
        try {
            sftpService.enviarArchivo(nombreArchivo, construirCsv(valores));
            resumen.put(nombreArchivo, valores.size());
        } catch (Exception e) {
            log.error("Error al subir {}: {}", nombreArchivo, e.getMessage(), e);
            resumen.put(nombreArchivo, "Error: " + e.getMessage());
        }
    }

    protected byte[] construirCsv(List<String> valores) {
        String contenido = valores.isEmpty() ? "" : String.join("\n", valores) + "\n";
        return contenido.getBytes(StandardCharsets.UTF_8);
    }
}
