package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.ConteoValidacionSl;
import com.mx.liverpool.automatizacionbackend.repository.ValidacionSlRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@Log4j2
public class ValidacionSlService {

    private static final Map<String, String> ARCHIVOS = new LinkedHashMap<>(Map.of(
            "existen", "existen.csv",
            "noexisten", "noexisten.csv",
            "historico", "existen_h.csv"));
    private static final List<String> CLAVES = List.of("existen", "noexisten", "historico");
    private static final int DIAS_SERIE = 30;

    private final SftpService sftpService;
    private final ValidacionSlRepository validacionSlRepository;
    private final String directorioEntrada;
    private final Path directorioLocal;

    @Autowired
    public ValidacionSlService(SftpService sftpService,
                               ValidacionSlRepository validacionSlRepository,
                               @Value("${sftp.directorio-entrada}") String directorioEntrada,
                               @Value("${validacion-sl.directorio-local}") String directorioLocal) {
        this.sftpService = sftpService;
        this.validacionSlRepository = validacionSlRepository;
        this.directorioEntrada = directorioEntrada;
        this.directorioLocal = Path.of(directorioLocal);
    }

    public Map<String, Object> descargarYGuardar() {
        log.info("Entrando a descargarYGuardar desde {}", directorioEntrada);
        List<String> nombres = CLAVES.stream().map(ARCHIVOS::get).toList();
        Map<String, byte[]> archivos = sftpService.descargarArchivos(directorioEntrada, nombres);
        try {
            Files.createDirectories(directorioLocal);
            for (Map.Entry<String, byte[]> archivo : archivos.entrySet()) {
                Files.write(directorioLocal.resolve(archivo.getKey()), archivo.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error al guardar los archivos de validación en disco", e);
        }
        validacionSlRepository.guardarConteo(LocalDate.now().toString(),
                contar("existen"), contar("noexisten"), contar("historico"));
        Map<String, Object> resumen = obtenerResumen();
        log.info("Finalizando descargarYGuardar: {}", resumen.get("conteos"));
        return resumen;
    }

    public Map<String, Object> obtenerResumen() {
        log.info("Entrando a obtenerResumen");
        Map<String, Integer> conteos = new LinkedHashMap<>();
        CLAVES.forEach(clave -> conteos.put(clave, contar(clave)));

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("fecha", fechaSnapshot());
        resumen.put("conteos", conteos);
        resumen.put("serie", validacionSlRepository.obtenerUltimosDias(DIAS_SERIE).reversed());
        log.info("Finalizando obtenerResumen: {}", conteos);
        return resumen;
    }

    public List<String> obtenerFilas(String clave, int desde, int tamano) {
        try (Stream<String> lineas = leer(clave)) {
            return lineas.skip(Math.max(desde, 0)).limit(Math.max(tamano, 0)).toList();
        }
    }

    public List<String> buscar(String remision) {
        log.info("Entrando a buscar la remisión {}", remision);
        String buscado = remision.trim();
        List<String> encontrado = new ArrayList<>();
        for (String clave : CLAVES) {
            try (Stream<String> lineas = leer(clave)) {
                if (lineas.anyMatch(linea -> linea.equals(buscado))) {
                    encontrado.add(clave);
                }
            }
        }
        log.info("Finalizando buscar la remisión {}: {}", remision, encontrado);
        return encontrado;
    }

    public byte[] descargar(String clave) {
        Path ruta = rutaDe(clave);
        if (!Files.exists(ruta)) {
            throw new IllegalArgumentException("Todavía no se ha descargado el archivo " + ARCHIVOS.get(clave) + ".");
        }
        try {
            return Files.readAllBytes(ruta);
        } catch (IOException e) {
            throw new UncheckedIOException("Error al leer " + ruta, e);
        }
    }

    public String nombreArchivo(String clave) {
        rutaDe(clave);
        return ARCHIVOS.get(clave);
    }

    public int contar(String clave) {
        try (Stream<String> lineas = leer(clave)) {
            return (int) lineas.count();
        }
    }

    private Stream<String> leer(String clave) {
        Path ruta = rutaDe(clave);
        if (!Files.exists(ruta)) {
            return Stream.empty();
        }
        try {
            return Files.lines(ruta, StandardCharsets.UTF_8).map(String::trim).filter(linea -> !linea.isEmpty());
        } catch (IOException e) {
            throw new UncheckedIOException("Error al leer " + ruta, e);
        }
    }

    private Path rutaDe(String clave) {
        String nombre = ARCHIVOS.get(clave);
        if (nombre == null) {
            throw new IllegalArgumentException("Archivo desconocido: " + clave + ". Use existen, noexisten o historico.");
        }
        return directorioLocal.resolve(nombre);
    }

    private LocalDateTime fechaSnapshot() {
        Path ruta = rutaDe("existen");
        if (!Files.exists(ruta)) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(ruta).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            throw new UncheckedIOException("Error al leer la fecha de " + ruta, e);
        }
    }
}
