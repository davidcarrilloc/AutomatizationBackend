package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/oms")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OMSController {
    private final OMSService omsService;
    private final ExcelService excelService;
    private final FaltantesOmsService faltantesOmsService;

    @PostMapping(value = "/verificarEnOMSLiverpool", consumes = {"multipart/form-data"})
    public ResponseEntity<?> verificarEnOMSOrdenVenta(@RequestParam("file") MultipartFile file) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Verificacion_OMS_LP.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                excelService.crearReporteVerificarEnOMSOrdenVenta(
                        omsService.massivePostOrder(
                                excelService.fromExcelToListOfRows(file, "0","0"), "LIVERPOOL"
                        )
                )
        );
    }

    @PostMapping(value = "/verificarEnOMSSuburbia", consumes = {"multipart/form-data"})
    public ResponseEntity<?> verificarEnOMSShippingGroup(@RequestParam("file") MultipartFile file) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Verificacion_OMS_SBB.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                    excelService.crearReporteVerificarEnOMSOrdenVenta(
                            omsService.massivePostOrder(
                                    excelService.fromExcelToListOfRows(file, "0","0"), "SUBURBIA"
                            )
                    )
                );
    }

    @GetMapping("/reporte/faltantes/liverpool")
    public ResponseEntity<?> obtenerReporteFaltantesLiverpool(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Faltantes_OMS_LP.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(faltantesOmsService.generarReporteLiverpool(inicio.atStartOfDay(), fin.atTime(23, 59, 59)));
    }

    @GetMapping("/reporte/faltantes/suburbia")
    public ResponseEntity<?> obtenerReporteFaltantesSuburbia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Faltantes_OMS_SBB.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(faltantesOmsService.generarReporteSuburbia(inicio.atStartOfDay(), fin.atTime(23, 59, 59)));
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }
}
