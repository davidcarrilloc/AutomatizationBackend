package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.FulfillmentService;
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
@RequestMapping("/api/v1/fulfillment")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Fulfillment", description = "Reproceso asíncrono de fulfillment por jobId")
public class FulfillmentController {
    private final FulfillmentService fulfillmentService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar reproceso de fulfillment",
            description = "Recibe un Excel con trackingnumbers en la columna A e inicia un reproceso asíncrono. Devuelve un jobId para consultar estatus y resultados.")
    @ApiResponse(responseCode = "202", description = "Reproceso aceptado; devuelve el jobId")
    @PostMapping(value = "/reproceso", consumes = {"multipart/form-data"})
    public ResponseEntity<?> reprocesarFulfillment(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) con los identificadores en la columna A") @RequestParam("file") MultipartFile file) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        return ResponseEntity.accepted().body(
                fulfillmentService.iniciarReproceso(
                        excelService.fromExcelToListOfRows(file, "0", "0")
                                .stream()
                                .map(row -> row.get(0))
                                .toList()
                )
        );
    }

    @Operation(summary = "Consultar estatus del reproceso",
            description = "Devuelve el estatus actual del reproceso de fulfillment identificado por jobId.")
    @ApiResponse(responseCode = "200", description = "Estatus del job de reproceso")
    @GetMapping("/reproceso/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusReproceso(
            @Parameter(description = "Identificador del job de reproceso") @PathVariable String jobId) {
        return ResponseEntity.ok(fulfillmentService.obtenerEstatus(jobId));
    }

    @Operation(summary = "Descargar resultados del reproceso",
            description = "Genera un .xlsx (descarga) con los resultados del reproceso de fulfillment identificado por jobId.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con los resultados del reproceso")
    @GetMapping("/reproceso/excel/{jobId}")
    public ResponseEntity<?> obtenerExcelReproceso(
            @Parameter(description = "Identificador del job de reproceso") @PathVariable String jobId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Fulfillment_" + jobId + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.crearReporteFulfillment(fulfillmentService.obtenerResultados(jobId)));
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }
}
