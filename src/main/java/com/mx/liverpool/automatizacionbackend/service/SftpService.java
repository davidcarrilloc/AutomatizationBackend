package com.mx.liverpool.automatizacionbackend.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class SftpService {

    private static final int TIMEOUT_MS = 30000;

    private final String host;
    private final int puerto;
    private final String usuario;
    private final String password;
    private final String directorio;

    @Autowired
    public SftpService(@Value("${sftp.host}") String host,
                       @Value("${sftp.puerto}") int puerto,
                       @Value("${sftp.usuario}") String usuario,
                       @Value("${sftp.password}") String password,
                       @Value("${sftp.directorio:}") String directorio) {
        this.host = host;
        this.puerto = puerto;
        this.usuario = usuario;
        this.password = password;
        this.directorio = directorio;
    }

    public void enviarArchivo(String nombreArchivo, byte[] contenido) {
        log.info("Entrando a enviarArchivo: {} ({} bytes)", nombreArchivo, contenido.length);
        Session sesion = null;
        ChannelSftp canal = null;
        try {
            JSch jsch = new JSch();
            sesion = jsch.getSession(usuario, host, puerto);
            sesion.setPassword(password);
            sesion.setConfig("StrictHostKeyChecking", "no");
            sesion.connect(TIMEOUT_MS);

            canal = (ChannelSftp) sesion.openChannel("sftp");
            canal.connect(TIMEOUT_MS);

            if (!directorio.isBlank()) {
                canal.cd(directorio);
            }
            canal.put(new ByteArrayInputStream(contenido), nombreArchivo);
            log.info("Archivo depositado en SFTP: {}", nombreArchivo);
        } catch (Exception e) {
            throw new RuntimeException("Error al depositar " + nombreArchivo + " en el SFTP: " + e.getMessage(), e);
        } finally {
            if (canal != null) {
                canal.disconnect();
            }
            if (sesion != null) {
                sesion.disconnect();
            }
        }
        log.info("Finalizando enviarArchivo: {}", nombreArchivo);
    }

    public Map<String, byte[]> descargarArchivos(String directorioRemoto, List<String> nombresArchivo) {
        log.info("Entrando a descargarArchivos: {} desde {}", nombresArchivo, directorioRemoto);
        Map<String, byte[]> archivos = new LinkedHashMap<>();
        Session sesion = null;
        ChannelSftp canal = null;
        try {
            JSch jsch = new JSch();
            sesion = jsch.getSession(usuario, host, puerto);
            sesion.setPassword(password);
            sesion.setConfig("StrictHostKeyChecking", "no");
            sesion.connect(TIMEOUT_MS);

            canal = (ChannelSftp) sesion.openChannel("sftp");
            canal.connect(TIMEOUT_MS);

            if (!directorioRemoto.isBlank()) {
                canal.cd(directorioRemoto);
            }
            for (String nombreArchivo : nombresArchivo) {
                try (InputStream entrada = canal.get(nombreArchivo)) {
                    archivos.put(nombreArchivo, entrada.readAllBytes());
                }
                log.info("Archivo descargado del SFTP: {} ({} bytes)", nombreArchivo, archivos.get(nombreArchivo).length);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al descargar " + nombresArchivo + " de " + directorioRemoto
                    + " en el SFTP: " + e.getMessage(), e);
        } finally {
            if (canal != null) {
                canal.disconnect();
            }
            if (sesion != null) {
                sesion.disconnect();
            }
        }
        log.info("Finalizando descargarArchivos: {} archivos", archivos.size());
        return archivos;
    }
}
