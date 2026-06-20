package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/upload")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name = "Upload", description = "Carga de archivos para reprocesos de marketplace y códigos digitales")
public class UploadController {
    private final FileService fileService;
    private final ExecutePythonService executePythonService;
    private final CancelacionAtgMkpService cancelacionAtgMkpService;
    private final CodigoDigitalService codigoDigitalService;
    private final ExcelService excelService;

    @Operation(summary = "Reproceso marketplace APV",
            description = "Recibe un archivo CSV, lo deposita en el directorio de procesamiento APV y ejecuta el script Python de reproceso. Devuelve la salida del script.")
    @ApiResponse(responseCode = "200", description = "Salida de la ejecución del script APV")
    @PostMapping(value = "/reprocesoMkpApv", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadApvFile(
            @Parameter(description = "Archivo CSV con las órdenes a reprocesar") @RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
        fileService.moveFileToProcessingApvDirectory(file);
        return ResponseEntity.ok(executePythonService.executeApvScript());
    }

    @Operation(summary = "Reproceso marketplace ATG",
            description = "Recibe un archivo CSV, lo deposita en el directorio de procesamiento ATG y ejecuta el script Python de reproceso. Devuelve la salida del script.")
    @ApiResponse(responseCode = "200", description = "Salida de la ejecución del script ATG")
    @PostMapping(value = "/reprocesoMkpAtg", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadAtgFile(
            @Parameter(description = "Archivo CSV con las órdenes a reprocesar") @RequestParam("file") MultipartFile file) {
        if (isNotCSVFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos CSV.");
        fileService.moveFileToProcessingAtgDirectory(file);
        return ResponseEntity.ok(executePythonService.executeAtgScript());
    }

    @Operation(summary = "Cancelación/devolución marketplace ATG",
            description = "Recibe un Excel con las columnas requeridas (0,1,2,7) y procesa las cancelaciones/devoluciones de marketplace ATG. Devuelve el resultado del proceso.")
    @ApiResponse(responseCode = "200", description = "Resultado del proceso de cancelaciones/devoluciones")
    @PostMapping(value = "/cancelacionDevolucionMkpAtg", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadCancelacionDevolucionMkpAtg(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) con las cancelaciones/devoluciones") @RequestParam("file") MultipartFile file) {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");
        return ResponseEntity.ok(cancelacionAtgMkpService.executeCancelacionesProcess(
                excelService.fromExcelToListOfRows(file, "0","0,1,2,7")
        ));
    }

    @Operation(summary = "Consultar códigos digitales",
            description = "Recibe un Excel con identificadores en la columna A y devuelve los códigos digitales asociados.")
    @ApiResponse(responseCode = "200", description = "Lista de códigos digitales encontrados")
    @PostMapping(value = "/codigosDigitales", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadCodigosDigitales(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) con los identificadores en la columna A") @RequestParam("file") MultipartFile file) {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");
        return ResponseEntity.ok(codigoDigitalService.obtenerCodigosDigitales(
                excelService.fromExcelToListOfRows(file, "0","0")
                        .stream()
                        .map(row -> row.get(0))
                        .toList()
        ));
    }

    private boolean isNotCSVFile(String fileName) {
        return fileName == null || !fileName.toLowerCase().endsWith(".csv");
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }
}
