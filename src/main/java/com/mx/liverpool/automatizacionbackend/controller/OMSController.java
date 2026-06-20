package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "OMS", description = "Verificación de órdenes en OMS Liverpool y reportes de faltantes")
public class OMSController {
    private final OMSService omsService;
    private final ExcelService excelService;
    private final FaltantesOmsService faltantesOmsService;

    @Operation(summary = "Verificar órdenes en OMS Liverpool",
            description = "Recibe un Excel con órdenes en la columna A, las verifica masivamente en OMS Liverpool y devuelve un .xlsx (descarga) con el resultado de la verificación.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con la verificación OMS Liverpool")
    @PostMapping(value = "/verificarEnOMSLiverpool", consumes = {"multipart/form-data"})
    public ResponseEntity<?> verificarEnOMSOrdenVenta(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) con las órdenes en la columna A") @RequestParam("file") MultipartFile file) throws IOException {
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

    @Operation(summary = "Verificar órdenes en OMS Suburbia",
            description = "Recibe un Excel con órdenes en la columna A, las verifica masivamente en OMS Suburbia y devuelve un .xlsx (descarga) con el resultado de la verificación.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con la verificación OMS Suburbia")
    @PostMapping(value = "/verificarEnOMSSuburbia", consumes = {"multipart/form-data"})
    public ResponseEntity<?> verificarEnOMSShippingGroup(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) con las órdenes en la columna A") @RequestParam("file") MultipartFile file) throws IOException {
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

    @Operation(summary = "Reporte de faltantes OMS Liverpool",
            description = "Genera un .xlsx (descarga) con las órdenes no enviadas a OMS Liverpool en el rango de fechas indicado.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con los faltantes de OMS Liverpool")
    @GetMapping("/reporte/faltantes/liverpool")
    public ResponseEntity<?> obtenerReporteFaltantesLiverpool(
            @Parameter(description = "Fecha inicial (yyyy-MM-dd)", example = "2026-06-01") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Fecha final (yyyy-MM-dd)", example = "2026-06-20") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Faltantes_OMS_LP.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(faltantesOmsService.generarReporteLiverpool(inicio.atStartOfDay(), fin.atTime(23, 59, 59)));
    }

    @Operation(summary = "Reporte de faltantes OMS Suburbia",
            description = "Genera un .xlsx (descarga) con las órdenes no enviadas a OMS Suburbia en el rango de fechas indicado.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con los faltantes de OMS Suburbia")
    @GetMapping("/reporte/faltantes/suburbia")
    public ResponseEntity<?> obtenerReporteFaltantesSuburbia(
            @Parameter(description = "Fecha inicial (yyyy-MM-dd)", example = "2026-06-01") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Fecha final (yyyy-MM-dd)", example = "2026-06-20") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) throws IOException {
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
