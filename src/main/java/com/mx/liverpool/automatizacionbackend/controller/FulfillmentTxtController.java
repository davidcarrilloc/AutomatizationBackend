package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.StatusOmsService;
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
@RequestMapping("/api/v1/fulfillment-txt")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Fulfillment TXT", description = "Consulta masiva del statusOms de una lista de trackingnumbers en .txt")
public class FulfillmentTxtController {
    private final StatusOmsService statusOmsService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar consulta masiva de statusOms",
            description = "Recibe un archivo .txt con un trackingnumber por línea e inicia una consulta asíncrona. " +
                    "Los trackings se rellenan a 10 dígitos con ceros a la izquierda y se consultan uno por uno, sin " +
                    "pausas; el que falla se encola al final y se reintenta en las rondas de reproceso. Devuelve un " +
                    "jobId para consultar estatus y resultados. Pueden correr varios jobs al mismo tiempo.")
    @ApiResponse(responseCode = "202", description = "Consulta aceptada; devuelve el jobId")
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> consultarStatusOms(
            @Parameter(description = "Archivo .txt con un trackingnumber por línea") @RequestParam("file") MultipartFile file) {
        if (excelService.esArchivoNoTxt(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos .txt.");

        return ResponseEntity.accepted().body(
                statusOmsService.iniciarProceso(excelService.leerLineasDeTxt(file))
        );
    }

    @Operation(summary = "Consultar estatus de la consulta masiva",
            description = "Devuelve el estatus actual de la consulta de statusOms identificada por jobId.")
    @ApiResponse(responseCode = "200", description = "Estatus del job de consulta")
    @GetMapping("/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusStatusOms(
            @Parameter(description = "Identificador del job de consulta", example = "1754500000000") @PathVariable String jobId) {
        return ResponseEntity.ok(statusOmsService.obtenerEstatus(jobId));
    }

    @Operation(summary = "Listar los jobs de consulta",
            description = "Devuelve todos los jobs de statusOms registrados desde el último arranque de la aplicación, " +
                    "del más reciente al más viejo. El campo estatus distingue los que siguen corriendo (EN_PROCESO, con " +
                    "fin en null) de los que ya acabaron (COMPLETADO o COMPLETADO_CON_ERRORES).")
    @ApiResponse(responseCode = "200", description = "Lista de jobs con su avance")
    @GetMapping("/jobs")
    public ResponseEntity<?> obtenerJobsStatusOms() {
        return ResponseEntity.ok(statusOmsService.obtenerJobs());
    }

    @Operation(summary = "Descargar resultados de la consulta",
            description = "Genera un .xlsx (descarga) de dos columnas, TrackingNumber y StatusOms, para el jobId indicado.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con TrackingNumber y StatusOms")
    @GetMapping("/excel/{jobId}")
    public ResponseEntity<?> obtenerExcelStatusOms(
            @Parameter(description = "Identificador del job de consulta", example = "1754500000000") @PathVariable String jobId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_StatusOms_" + jobId + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.crearReporteStatusOms(statusOmsService.obtenerResultados(jobId)));
    }
}
