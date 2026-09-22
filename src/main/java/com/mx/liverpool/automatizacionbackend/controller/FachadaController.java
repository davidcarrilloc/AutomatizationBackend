package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.FachadaService;
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
@RequestMapping("/api/v1/fachada")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Fachada", description = "Reenvío masivo y concurrente de órdenes tal cual a la fachada I200, por jobId")
public class FachadaController {
    private final FachadaService fachadaService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar reproceso masivo contra la fachada I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber) e inicia un reproceso " +
                    "asíncrono. Cada JSON se envía tal cual (pass-through) a la fachada I200. El Excel se parte en bloques " +
                    "(fachada.tamano-bloque, default 1 000) y se procesan hasta W = availableProcessors × fachada.factor " +
                    "bloques en paralelo con hilos virtuales, sin pausas entre envíos. La fila que da error se manda al " +
                    "final de la cola de su bloque y se reintenta hasta fachada.max-rondas. Devuelve un jobId para consultar " +
                    "estatus y descargar resultados.")
    @ApiResponse(responseCode = "202", description = "Reproceso aceptado; devuelve el jobId y su estatus")
    @PostMapping(value = "/reproceso", consumes = {"multipart/form-data"})
    public ResponseEntity<?> reprocesarFachada(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=TrackingNumber") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        return ResponseEntity.accepted().body(
                fachadaService.iniciarReproceso(excelService.leerReprocesoNode(file))
        );
    }

    @Operation(summary = "Consultar estatus del reproceso de fachada",
            description = "Devuelve el estatus actual del reproceso identificado por jobId (total, procesados, errores, bloques).")
    @ApiResponse(responseCode = "200", description = "Estatus del job de reproceso")
    @GetMapping("/reproceso/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusReproceso(
            @Parameter(description = "Identificador del job de reproceso") @PathVariable String jobId) {
        return ResponseEntity.ok(fachadaService.obtenerEstatus(jobId));
    }

    @Operation(summary = "Listar los jobs de reproceso de fachada",
            description = "Devuelve todos los jobs registrados desde el último arranque de la aplicación, del más reciente " +
                    "al más viejo. El campo estatus distingue los que siguen corriendo (EN_PROCESO, con fin en null) de los " +
                    "que ya acabaron (COMPLETADO o COMPLETADO_CON_ERRORES).")
    @ApiResponse(responseCode = "200", description = "Lista de jobs con su avance")
    @GetMapping("/reproceso/jobs")
    public ResponseEntity<?> obtenerJobsReproceso() {
        return ResponseEntity.ok(fachadaService.obtenerJobs());
    }

    @Operation(summary = "Descargar resultados del reproceso de fachada",
            description = "Genera un .xlsx (descarga) con los resultados del reproceso identificado por jobId, con columnas " +
                    "Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con los resultados del reproceso")
    @GetMapping("/reproceso/excel/{jobId}")
    public ResponseEntity<?> obtenerExcelReproceso(
            @Parameter(description = "Identificador del job de reproceso") @PathVariable String jobId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Fachada_" + jobId + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.crearReporteReproceso(fachadaService.obtenerResultados(jobId)));
    }
}
