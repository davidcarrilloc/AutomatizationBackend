package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.CorreoTxService;
import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/v1/correos")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Correos", description = "Procesamiento asíncrono de transacciones por correo por jobId")
public class CorreosController {
    private final CorreoTxService correoTxService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar procesamiento de transacciones por correo",
            description = "Recibe un archivo .zip con los correos segmentados e inicia un procesamiento asíncrono. Devuelve un jobId para consultar el estatus.")
    @ApiResponse(responseCode = "202", description = "Procesamiento aceptado; devuelve el jobId")
    @PostMapping(value = "/transaccionesPorCorreo", consumes = {"multipart/form-data"})
    public ResponseEntity<?> obtenerTransaccionesPorCorreo(
            @Parameter(description = "Archivo .zip con los correos segmentados") @RequestParam("file") MultipartFile file) {
        if (isNotZipFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos ZIP.");

        LinkedHashMap<String, List<String>> segmentos = excelService.leerCorreosPorSegmento(file);
        return ResponseEntity.accepted().body(correoTxService.iniciarProcesamiento(segmentos));
    }

    @Operation(summary = "Consultar estatus del procesamiento",
            description = "Devuelve el estatus actual del procesamiento de transacciones por correo identificado por jobId.")
    @ApiResponse(responseCode = "200", description = "Estatus del job de procesamiento")
    @GetMapping("/transaccionesPorCorreo/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusProcesamiento(
            @Parameter(description = "Identificador del job de procesamiento") @PathVariable String jobId) {
        return ResponseEntity.ok(correoTxService.obtenerEstatus(jobId));
    }

    private boolean isNotZipFile(String fileName) {
        return fileName == null || !fileName.toLowerCase().endsWith(".zip");
    }
}
