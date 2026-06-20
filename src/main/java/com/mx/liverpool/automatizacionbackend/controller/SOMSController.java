package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.OrdenSomsService;
import com.mx.liverpool.automatizacionbackend.service.SOMSService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "SOMS", description = "Reportes y consultas sobre órdenes del sistema SOMS")
public class SOMSController {
    private final SOMSService somsService;
    private final OrdenSomsService ordenSomsService;
    private final ExcelService excelService;

    @Operation(summary = "Reporte de remisiones sin datos",
            description = "Recibe un archivo .txt con una remisión por línea, valida cobro (Oracle) y HRD, " +
                    "y devuelve un JSON con la clasificación de las remisiones (cobradas, no cobradas, con/sin HRD).")
    @ApiResponse(responseCode = "200", description = "Reporte JSON con el universo clasificado de remisiones")
    @PostMapping(value = "/reporte/remisiones-sin-datos", consumes = {"multipart/form-data"})
    public ResponseEntity<?> detalleTx(
            @Parameter(description = "Archivo .txt con una remisión por línea") @RequestParam("file") MultipartFile file) {
        if (isNotTxtFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos .txt.");
        return ResponseEntity.ok(somsService.obtenerReporte(file));
    }

    @Operation(summary = "Consultar órdenes en SOMS (muestreo aleatorio)",
            description = "Recibe un Excel con remisiones (columna A), toma una muestra aleatoria de tamaño 'muestra' " +
                    "y consulta cada una contra el servicio SOAP de SOMS espaciando las llamadas (2 s entre consultas, " +
                    "10 s cada 10) para no saturar el servicio. Devuelve un .xlsx con Remision, Status Datos, Status SOMS, " +
                    "Nodo Destinatario y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado de la muestra consultada")
    @PostMapping(value = "/consultarOrdenes", consumes = {"multipart/form-data"})
    public ResponseEntity<?> consultarOrdenes(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) con las remisiones en la columna A") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Cantidad de remisiones a consultar al azar", example = "5") @RequestParam(value = "muestra", defaultValue = "5") int muestra) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Ordenes_SOMS.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteOrdenSoms(
                                ordenSomsService.consultarOrdenes(
                                        excelService.leerRemisionesDeExcel(file), muestra
                                )
                        )
                );
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }

    private boolean isNotTxtFile(String fileName) {
        return fileName == null || !fileName.toLowerCase().endsWith(".txt");
    }
}
