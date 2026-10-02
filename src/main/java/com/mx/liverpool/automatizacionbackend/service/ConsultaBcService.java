package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.repository.RemisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Consulta síncrona del volcado de transacciones no enviadas a OMS por remisión. Lee un Excel de
 * remisiones, consulta BRIDGECORE en lotes de 1000 (límite del IN de Oracle) y devuelve el .xlsx.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class ConsultaBcService {
    // El IN (...) de Oracle no admite más de 1000 elementos: las remisiones se consultan por lotes.
    private static final int TAMANO_LOTE_ORACLE = 1000;

    private final ExcelService excelService;
    private final RemisionRepository remisionRepository;

    public byte[] consultarNoOms(MultipartFile file) throws IOException {
        log.info("Entrando a consultarNoOms");
        byte[] reporte = consultarPorLotes(file, remisionRepository::obtenerBcNoOms);
        log.info("Finalizando consultarNoOms");
        return reporte;
    }

    public byte[] consultarNoOmsPorOrdenVenta(MultipartFile file) throws IOException {
        log.info("Entrando a consultarNoOmsPorOrdenVenta");
        byte[] reporte = consultarPorLotes(file, remisionRepository::obtenerBcNoOmsPorOrdenVenta);
        log.info("Finalizando consultarNoOmsPorOrdenVenta");
        return reporte;
    }

    public byte[] consultarNoOmsPorShippingGroup(MultipartFile file) throws IOException {
        log.info("Entrando a consultarNoOmsPorShippingGroup");
        byte[] reporte = consultarPorLotes(file, remisionRepository::obtenerBcNoOmsPorShippingGroup);
        log.info("Finalizando consultarNoOmsPorShippingGroup");
        return reporte;
    }

    // Lee la columna A del Excel y la consulta en lotes con la consulta dada; el reporte es el mismo para las tres llaves.
    private byte[] consultarPorLotes(MultipartFile file, Function<List<String>, List<Map<String, Object>>> consulta) throws IOException {
        List<String> valores = excelService.leerRemisionesDeExcel(file);

        List<Map<String, Object>> filas = new ArrayList<>();
        for (List<String> lote : particionar(valores, TAMANO_LOTE_ORACLE)) {
            filas.addAll(consulta.apply(lote));
        }

        log.info("Consultados {} valores, {} filas", valores.size(), filas.size());
        return excelService.crearReporteBc(filas);
    }

    // Trocea la lista en sublistas de a lo más tamano elementos, conservando el orden.
    static List<List<String>> particionar(List<String> elementos, int tamano) {
        List<List<String>> lotes = new ArrayList<>();
        for (int i = 0; i < elementos.size(); i += tamano) {
            lotes.add(new ArrayList<>(elementos.subList(i, Math.min(i + tamano, elementos.size()))));
        }
        return lotes;
    }
}
