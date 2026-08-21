package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.AvailabilityService;
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
@RequestMapping("/api/v1/availability")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Availability", description = "Consulta masiva del stock (availability.availableQuantity) de un SKU dentro de la orden de su remisión")
public class AvailabilityController {
    private final AvailabilityService availabilityService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar búsqueda masiva de availability",
            description = "Recibe un Excel de dos columnas (A: SKU, B: remisión) e inicia una búsqueda asíncrona. " +
                    "Por cada fila consulta la remisión en OGCP para obtener el ctOrderId y con él pide la orden a " +
                    "commercetools, de donde toma el availability.availableQuantity del lineItem cuyo variant.sku " +
                    "empata con el SKU de la columna A. Devuelve un jobId para consultar estatus y resultados.")
    @ApiResponse(responseCode = "202", description = "Búsqueda aceptada; devuelve el jobId")
    @PostMapping(value = "/available", consumes = {"multipart/form-data"})
    public ResponseEntity<?> reprocesarAvailability(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=SKU, B=remisión") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Secret (password) del API client de commercetools con el que se genera el access_token. No se almacena ni se registra en bitácora.") @RequestParam("password") String password) {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        return ResponseEntity.accepted().body(
                availabilityService.iniciarReproceso(excelService.leerAvailability(file), password)
        );
    }

    @Operation(summary = "Consultar estatus de la búsqueda",
            description = "Devuelve el estatus actual de la búsqueda de availability identificada por jobId.")
    @ApiResponse(responseCode = "200", description = "Estatus del job de búsqueda")
    @GetMapping("/available/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusAvailability(
            @Parameter(description = "Identificador del job de búsqueda", example = "1754500000000") @PathVariable String jobId) {
        return ResponseEntity.ok(availabilityService.obtenerEstatus(jobId));
    }

    @Operation(summary = "Listar los jobs de búsqueda",
            description = "Devuelve todos los jobs de availability registrados desde el último arranque de la aplicación, " +
                    "del más reciente al más viejo. El campo estatus distingue los que siguen corriendo (EN_PROCESO, con " +
                    "fin en null) de los que ya acabaron (COMPLETADO o COMPLETADO_CON_ERRORES).")
    @ApiResponse(responseCode = "200", description = "Lista de jobs con su avance")
    @GetMapping("/available/jobs")
    public ResponseEntity<?> obtenerJobsAvailability() {
        return ResponseEntity.ok(availabilityService.obtenerJobs());
    }

    @Operation(summary = "Descargar resultados de la búsqueda",
            description = "Genera un .xlsx (descarga) con columnas SKU, Remisión y Stock para el jobId indicado.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con los resultados de la búsqueda")
    @GetMapping("/available/excel/{jobId}")
    public ResponseEntity<?> obtenerExcelAvailability(
            @Parameter(description = "Identificador del job de búsqueda", example = "1754500000000") @PathVariable String jobId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Availability_" + jobId + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.crearReporteAvailability(availabilityService.obtenerResultados(jobId)));
    }
}
