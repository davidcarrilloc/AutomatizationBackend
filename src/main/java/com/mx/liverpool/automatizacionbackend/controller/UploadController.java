package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExecutePythonService;
import com.mx.liverpool.automatizacionbackend.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/upload")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UploadController {
    private final FileService fileService;
    private final ExecutePythonService executePythonService;

    @PostMapping("/apv")
    public ResponseEntity<?> uploadApvFile(@RequestParam("file") MultipartFile file) {
        if (!isCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
        fileService.moveFileToProcessingApvDirectory(file);
        return ResponseEntity.ok(executePythonService.executeApvScript());
    }

    @PostMapping("/atg")
    public ResponseEntity<?> uploadAtgFile(@RequestParam("file") MultipartFile file) {
        if (!isCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
        fileService.moveFileToProcessingAtgDirectory(file);
        return ResponseEntity.ok(executePythonService.executeAtgScript());
    }

    @PostMapping("/cancelaciones")
    public void uploadCancelacionesFile(@RequestParam("file") MultipartFile file) {
        if (!isCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
//        fileService.moveFileToProcessingCancelacionesDirectory(file);
//        executePythonService.executeCancelacionesScript();
    }

    @PostMapping("/remisionSinDatos")
    public void uploadRemisionSinDatosFile(@RequestParam("file") MultipartFile file) {
        if (!isCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
//        fileService.moveFileToProcessingRemisionSinDatosDirectory(file);
//        executePythonService.executeRemisionSinDatosScript();
    }

    private boolean isCSVFile(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".csv");
    }
}
