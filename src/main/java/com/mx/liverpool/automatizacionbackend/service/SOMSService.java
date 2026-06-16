package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.BC;
import com.mx.liverpool.automatizacionbackend.model.Hrd;
import com.mx.liverpool.automatizacionbackend.payload.response.SOMSResponse;
import com.mx.liverpool.automatizacionbackend.repository.AtgMirklRepository;
import com.mx.liverpool.automatizacionbackend.repository.TxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Log4j2
public class SOMSService {
    private final TxRepository txRepository;
    private final AtgMirklRepository atgMirklRepository;

    private List<String> leerRemisiones(MultipartFile file) {
        log.info("Entrando a leerRemisiones");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> remisiones = reader.lines()
                    .map(String::trim)
                    .filter(linea -> !linea.isEmpty())
                    .toList();
            log.info("Finalizando leerRemisiones con {} remisiones", remisiones.size());
            return remisiones;
        } catch (IOException e) {
            log.error("Error al leer el archivo de remisiones: {}", e.getMessage());
            throw new RuntimeException("Error al leer el archivo de remisiones: " + e.getMessage());
        }
    }

    private List<String> limpiarCerosIzquierda(List<String> universo) {
        List<String> resultado = new ArrayList<>();
        for (String remision : universo) {
            resultado.add(remision.replaceFirst("^0+(?!$)", ""));
        }
        return resultado;
    }

    private Object ejecutarConsultas(List<String> universo) {
        universo = limpiarCerosIzquierda(universo);
        List<BC> resultSetCobro = txRepository.obtenerBC(universo);
        List<BC> atgCobradas = new ArrayList<>();
        List<BC> decommCobradas = new ArrayList<>();
        List<String> noCobradas = new ArrayList<>(universo);
        List<Hrd> hrd = new ArrayList<>();
        List<String> noHrd = new ArrayList<>();

        for (var element : resultSetCobro) {
            if (element.getAtgOrderId() == null) {
                atgCobradas.add(element);
            } else if (element.getAtgOrderId().startsWith("o")) {
                atgCobradas.add(element);
            } else {
                decommCobradas.add(element);
            }
        }

        noCobradas.removeAll(resultSetCobro.stream()
                .map(BC::getRemision)
                .toList());

        hrd = atgMirklRepository.obtenerHrd(atgCobradas.stream()
                .map(BC::getRemision)
                .toList());

        noHrd = new ArrayList<>(atgCobradas.stream()
                .map(BC::getRemision)
                .toList());

        noHrd.removeAll(hrd.stream()
                .map(Hrd::getTrackingNumber)
                .toList());

        return SOMSResponse.builder()
                .universo(universo)
                .atgCobradas(atgCobradas)
                .decommCobradas(decommCobradas)
                .noCobradas(noCobradas)
                .hrd(hrd)
                .noHrd(noHrd)
                .build();
    }

    public Object obtenerReporte(MultipartFile file) {
        log.info("Entrando a obtenerReporte");
        var resultSet = leerRemisiones(file);
        var reporte = ejecutarConsultas(resultSet);
        log.info("Finalizando obtenerReporte");
        return reporte;
    }
}
