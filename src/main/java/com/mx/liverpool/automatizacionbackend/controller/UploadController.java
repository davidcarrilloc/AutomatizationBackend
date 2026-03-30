package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.*;
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
    private final CancelacionAtgMkpService cancelacionAtgMkpService;
    private final CodigoDigitalService codigoDigitalService;
    private final TxService txService;
    private final ExcelService excelService;

    @PostMapping(value = "/reprocesoMkpApv", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadApvFile(@RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
        fileService.moveFileToProcessingApvDirectory(file);
        return ResponseEntity.ok(executePythonService.executeApvScript());
    }

    @PostMapping(value = "/reprocesoMkpAtg", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadAtgFile(@RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
        fileService.moveFileToProcessingAtgDirectory(file);
        return ResponseEntity.ok(executePythonService.executeAtgScript());
    }

    @PostMapping(value = "/cancelacionDevolucionMkpAtg", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadCancelacionDevolucionMkpAtg(@RequestParam("file") MultipartFile file) {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");
        return ResponseEntity.ok(cancelacionAtgMkpService.executeCancelacionesProcess(
                excelService.fromExcelToListOfRows(file, "0","0,1,2,7")
        ));
    }

    @PostMapping(value = "/remisionSinDatos", consumes = {"multipart/form-data"})
    public void uploadRemisionSinDatosFile(@RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
//        fileService.moveFileToProcessingRemisionSinDatosDirectory(file);
//        executePythonService.executeRemisionSinDatosScript();
    }

    @PostMapping(value = "/visualizarSoms", consumes = {"multipart/form-data"})
    public void uploadVisualizarSoms(@RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
    }

    @PostMapping(value = "/visualizarOms", consumes = {"multipart/form-data"})
    public void uploadVisualizarOms(@RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
    }

    @PostMapping(value = "/obtenerXmlSoms", consumes = {"multipart/form-data"})
    public void uploadObtenerXmlSoms(@RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
    }

    @PostMapping(value = "/obtenerXmlSterling", consumes = {"multipart/form-data"})
    public void uploadObtenerXmlOms(@RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
    }

    @PostMapping(value = "/suburbiaReproceso", consumes = {"multipart/form-data"})
    public void uploadSuburbiaReproceso(@RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
    }

    @PostMapping(value = "/codigosDigitales", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadCodigosDigitales(@RequestParam("file") MultipartFile file) {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");
        return ResponseEntity.ok(codigoDigitalService.obtenerCodigosDigitales(
                excelService.fromExcelToListOfRows(file, "0","0")
                        .stream()
                        .map(row -> row.get(0))
                        .toList()
        ));
    }

    @GetMapping("/obtenerDiferenciaTx")
    public ResponseEntity<?> diferenciaTxHoyvsAyer() {
        return ResponseEntity.ok(txService.obtenerDiferenciaTxHoyvsAyer());
    }

    private boolean isNotCSVFile(String fileName) {
        return fileName == null || !fileName.toLowerCase().endsWith(".csv");
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }
}
