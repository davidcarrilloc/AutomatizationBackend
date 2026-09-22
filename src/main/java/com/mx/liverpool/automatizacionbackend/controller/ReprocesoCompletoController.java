package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoCompletoAsyncService;
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
@RequestMapping("/api/v1/reproceso/completo")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Reproceso Completo Asíncrono", description = "Reproceso completo (F001 + Item + Correo + BillTo) contra I200 por jobId")
public class ReprocesoCompletoController {
    private final ReprocesoCompletoAsyncService reprocesoCompletoAsyncService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar el reproceso completo asíncrono contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: remisión) e inicia un reproceso asíncrono. " +
                    "Aplica las mismas correcciones que /completo/procesar (F001→001, Item y precios, EMailID/nombres y " +
                    "PersonInfoBillTo desde BRIDGECORE) y envía cada orden al servicio I200 en una sola pasada, sin pausas " +
                    "largas entre envíos. La fila con error de gateway (500/504) se difiere al final y se reintenta hasta " +
                    "reproceso.completo.max-rondas. Devuelve un jobId para consultar estatus y descargar resultados.")
    @ApiResponse(responseCode = "202", description = "Reproceso aceptado; devuelve el jobId y su estatus")
    @PostMapping(value = "/async", consumes = {"multipart/form-data"})
    public ResponseEntity<?> iniciarReprocesoCompleto(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=remisión") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        return ResponseEntity.accepted().body(
                reprocesoCompletoAsyncService.iniciarReproceso(excelService.leerReprocesoNode(file))
        );
    }

    @Operation(summary = "Consultar estatus del reproceso completo asíncrono",
            description = "Devuelve el estatus actual del reproceso identificado por jobId (total, procesados, con error de " +
                    "gateway, reprocesados y la remisión en curso).")
    @ApiResponse(responseCode = "200", description = "Estatus del job de reproceso")
    @GetMapping("/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusReprocesoCompleto(
            @Parameter(description = "Identificador del job de reproceso") @PathVariable String jobId) {
        return ResponseEntity.ok(reprocesoCompletoAsyncService.obtenerEstatus(jobId));
    }

    @Operation(summary = "Listar los jobs del reproceso completo asíncrono",
            description = "Devuelve todos los jobs registrados desde el último arranque de la aplicación, del más reciente al " +
                    "más viejo. El campo estatus distingue los que siguen corriendo (EN_PROCESO, con fin en null) de los que " +
                    "ya acabaron (COMPLETADO o COMPLETADO_CON_ERRORES).")
    @ApiResponse(responseCode = "200", description = "Lista de jobs con su avance")
    @GetMapping("/jobs")
    public ResponseEntity<?> obtenerJobsReprocesoCompleto() {
        return ResponseEntity.ok(reprocesoCompletoAsyncService.obtenerJobs());
    }

    @Operation(summary = "Descargar resultados del reproceso completo asíncrono",
            description = "Genera un .xlsx (descarga) con los resultados del reproceso identificado por jobId, con columnas " +
                    "Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con los resultados del reproceso")
    @GetMapping("/excel/{jobId}")
    public ResponseEntity<?> obtenerExcelReprocesoCompleto(
            @Parameter(description = "Identificador del job de reproceso") @PathVariable String jobId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_Completo_" + jobId + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.crearReporteReproceso(reprocesoCompletoAsyncService.obtenerResultados(jobId)));
    }
}
