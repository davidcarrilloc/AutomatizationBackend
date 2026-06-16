package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.CorreoTxService;
import com.mx.liverpool.automatizacionbackend.service.ExcelService;
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
public class CorreosController {
    private final CorreoTxService correoTxService;
    private final ExcelService excelService;

    @PostMapping(value = "/transaccionesPorCorreo", consumes = {"multipart/form-data"})
    public ResponseEntity<?> obtenerTransaccionesPorCorreo(@RequestParam("file") MultipartFile file) {
        if (isNotZipFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos ZIP.");

        LinkedHashMap<String, List<String>> segmentos = excelService.leerCorreosPorSegmento(file);
        return ResponseEntity.accepted().body(correoTxService.iniciarProcesamiento(segmentos));
    }

    @GetMapping("/transaccionesPorCorreo/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusProcesamiento(@PathVariable String jobId) {
        return ResponseEntity.ok(correoTxService.obtenerEstatus(jobId));
    }

    private boolean isNotZipFile(String fileName) {
        return fileName == null || !fileName.toLowerCase().endsWith(".zip");
    }
}
