package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.OrdenSomsService;
import com.mx.liverpool.automatizacionbackend.service.SOMSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/soms")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
public class SOMSController {
    private final SOMSService somsService;
    private final OrdenSomsService ordenSomsService;
    private final ExcelService excelService;

    @PostMapping("/reporte/remisiones-sin-datos")
    public ResponseEntity<?> detalleTx() {
        return ResponseEntity.ok(somsService.obtenerReporte());
    }

    @PostMapping(value = "/consultarOrdenes", consumes = {"multipart/form-data"})
    public ResponseEntity<?> consultarOrdenes(@RequestParam("file") MultipartFile file) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Ordenes_SOMS.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteOrdenSoms(
                                ordenSomsService.consultarOrdenes(
                                        excelService.leerRemisionesDeExcel(file)
                                )
                        )
                );
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }
}
