package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/oms")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OMSController {
    private final OMSService omsService;
    private final ExcelService excelService;

    @PostMapping(value = "/verificarEnOMSOrdenVenta", consumes = {"multipart/form-data"})
    public ResponseEntity<?> verificarEnOMSOrdenVenta(@RequestParam("file") MultipartFile file) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Verificacion_OMS_LP.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                excelService.crearReporteVerificarEnOMSOrdenVenta(
                        omsService.massivePostOrder(
                                excelService.fromExcelToListOfRows(file, "0","0"), "LIVERPOOL"
                        )
                )
        );
    }

    @PostMapping(value = "/verificarEnOMSShippingGroup", consumes = {"multipart/form-data"})
    public ResponseEntity<?> verificarEnOMSShippingGroup(@RequestParam("file") MultipartFile file) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Verificacion_OMS_SBB.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                    excelService.crearReporteVerificarEnOMSOrdenVenta(
                            omsService.massivePostOrder(
                                    excelService.fromExcelToListOfRows(file, "0","0"), "SUBURBIA"
                            )
                    )
                );
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }
}
