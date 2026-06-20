package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.OmsFaltante;
import com.mx.liverpool.automatizacionbackend.repository.FaltantesOmsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Log4j2
public class FaltantesOmsService {
    private final FaltantesOmsRepository faltantesOmsRepository;
    private final OMSService omsService;
    private final ExcelService excelService;

    public byte[] generarReporteLiverpool(LocalDateTime inicio, LocalDateTime fin) throws IOException {
        log.info("Entrando a generarReporteLiverpool. inicio: {}, fin: {}", inicio, fin);
        List<OmsFaltante> filas = faltantesOmsRepository.obtenerFaltantesLiverpool(inicio, fin);
        Map<String, Map<String, Object>> omsResult = consultarEnOMS(filas, OmsFaltante::getOrdenVenta, "LIVERPOOL");
        byte[] reporte = excelService.crearReporteFaltantes(filas, omsResult, OmsFaltante::getOrdenVenta);
        log.info("Finalizando generarReporteLiverpool con {} filas", filas.size());
        return reporte;
    }

    public byte[] generarReporteSuburbia(LocalDateTime inicio, LocalDateTime fin) throws IOException {
        log.info("Entrando a generarReporteSuburbia. inicio: {}, fin: {}", inicio, fin);
        List<OmsFaltante> filas = faltantesOmsRepository.obtenerFaltantesSuburbia(inicio, fin);
        Map<String, Map<String, Object>> omsResult = consultarEnOMS(filas, OmsFaltante::getAtgShipGrpId, "SUBURBIA");
        byte[] reporte = excelService.crearReporteFaltantes(filas, omsResult, OmsFaltante::getAtgShipGrpId);
        log.info("Finalizando generarReporteSuburbia con {} filas", filas.size());
        return reporte;
    }

    private Map<String, Map<String, Object>> consultarEnOMS(List<OmsFaltante> filas,
                                                            Function<OmsFaltante, String> llave,
                                                            String sbbOrLp) {
        List<Map<Integer, String>> ids = filas.stream()
                .map(llave)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .map(id -> {
                    Map<Integer, String> fila = new LinkedHashMap<>();
                    fila.put(0, id);
                    return fila;
                })
                .toList();
        log.info("Consultando {} identificadores en OMS ({})", ids.size(), sbbOrLp);
        return omsService.massivePostOrder(ids, sbbOrLp);
    }
}
