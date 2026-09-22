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

        List<String> remisiones = excelService.leerRemisionesDeExcel(file);

        List<Map<String, Object>> filas = new ArrayList<>();
        for (List<String> lote : particionar(remisiones, TAMANO_LOTE_ORACLE)) {
            filas.addAll(remisionRepository.obtenerBcNoOms(lote));
        }

        byte[] reporte = excelService.crearReporteBc(filas);
        log.info("Finalizando consultarNoOms con {} remisiones y {} filas", remisiones.size(), filas.size());
        return reporte;
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
