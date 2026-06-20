package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.FulfillmentService;
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
public class FulfillmentController {
    private final FulfillmentService fulfillmentService;
    private final ExcelService excelService;

    @PostMapping(value = "/reproceso", consumes = {"multipart/form-data"})
    public ResponseEntity<?> reprocesarFulfillment(@RequestParam("file") MultipartFile file) throws IOException {
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

    @GetMapping("/reproceso/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusReproceso(@PathVariable String jobId) {
        return ResponseEntity.ok(fulfillmentService.obtenerEstatus(jobId));
    }

    @GetMapping("/reproceso/excel/{jobId}")
    public ResponseEntity<?> obtenerExcelReproceso(@PathVariable String jobId) throws IOException {
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
