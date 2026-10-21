package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ApvService;
import com.mx.liverpool.automatizacionbackend.service.ExcelService;
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
@RequestMapping("/api/v1/apv")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "APV", description = "Reproceso asíncrono de remisiones APV (resend a Sterling + envío a la fachada I200) por jobId")
public class ApvController {
    private final ApvService apvService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar reproceso de remisiones APV",
            description = "Recibe un Excel con las remisiones en la columna A (las celdas no numéricas se omiten) e inicia un " +
                    "reproceso asíncrono: manda todas las remisiones en un TXT al resend de Sterling, rescata con dos " +
                    "consultas el STERLING_RAW_REQUEST_JSON de todas en APPSALESMAN y envía cada uno tal cual a la fachada " +
                    "I200. La remisión con error de fachada espera 3 s, se difiere al final y se reintenta hasta 3 rondas; " +
                    "la que no trae JSON queda con error sin reintento. Devuelve un jobId para consultar estatus y resultados.")
    @ApiResponse(responseCode = "202", description = "Reproceso aceptado; devuelve el jobId")
    @PostMapping(value = "/reproceso", consumes = {"multipart/form-data"})
    public ResponseEntity<?> reprocesarApv(
            @Parameter(description = "Archivo Excel (.xlsx) con las remisiones en la columna A") @RequestParam("file") MultipartFile file) {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        return ResponseEntity.accepted().body(apvService.iniciarReproceso(excelService.leerRemisionesDeExcel(file)));
    }

    @Operation(summary = "Consultar estatus del reproceso APV",
            description = "Devuelve el estatus actual del reproceso APV identificado por jobId.")
    @ApiResponse(responseCode = "200", description = "Estatus del job de reproceso")
    @GetMapping("/reproceso/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusReproceso(
            @Parameter(description = "Identificador del job de reproceso", example = "1759760000000") @PathVariable String jobId) {
        return ResponseEntity.ok(apvService.obtenerEstatus(jobId));
    }

    @Operation(summary = "Listar los jobs de reproceso APV",
            description = "Devuelve todos los jobs de reproceso APV registrados desde el último arranque de la aplicación, " +
                    "del más reciente al más viejo. El campo estatus distingue los que siguen corriendo (EN_PROCESO, con " +
                    "fin en null) de los que ya acabaron (COMPLETADO o COMPLETADO_CON_ERRORES).")
    @ApiResponse(responseCode = "200", description = "Lista de jobs con su avance")
    @GetMapping("/reproceso/jobs")
    public ResponseEntity<?> obtenerJobsReproceso() {
        return ResponseEntity.ok(apvService.obtenerJobs());
    }

    @Operation(summary = "Descargar resultados del reproceso APV",
            description = "Genera un .xlsx (descarga) con los resultados del job: Remisión, Resend, JSON obtenido, " +
                    "Respuesta fachada y Errores. Disponible cuando el job termina.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con los resultados del reproceso")
    @GetMapping("/reproceso/excel/{jobId}")
    public ResponseEntity<?> obtenerExcelReproceso(
            @Parameter(description = "Identificador del job de reproceso", example = "1759760000000") @PathVariable String jobId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_APV_" + jobId + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.crearReporteApv(apvService.obtenerResultados(jobId)));
    }
}
