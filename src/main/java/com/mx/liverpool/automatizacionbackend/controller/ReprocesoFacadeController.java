package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoFacadeService;
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
@RequestMapping("/api/v1/reproceso-facade")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Reproceso Facade", description = "Reenvío masivo de órdenes al servicio I200 (Apigee) a partir de un Excel")
public class ReprocesoFacadeController {
    private final ReprocesoFacadeService reprocesoFacadeService;
    private final ExcelService excelService;

    @Operation(summary = "Reprocesar órdenes contra I200",
            description = "Recibe un Excel de tres columnas (A: JSON del pedido, B: remisión, C: ItemID). Por cada fila " +
                    "reemplaza el ItemID del JSON con el valor de la columna C y envía la orden una por una al servicio I200 " +
                    "de Apigee, espaciando las llamadas (2 s entre envíos y 10 s cada 10). Devuelve un .xlsx (descarga) con " +
                    "columnas: Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarReproceso(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de tres columnas: A=JSON, B=remisión, C=ItemID") @RequestParam("file") MultipartFile file) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_Facade.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReprocesoFacade(
                                reprocesoFacadeService.reprocesar(
                                        excelService.leerReprocesoFacade(file)
                                )
                        )
                );
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }
}
