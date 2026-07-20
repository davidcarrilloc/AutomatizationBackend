package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoNodeService;
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
@RequestMapping("/api/v1/reproceso-node")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Reproceso Node", description = "Reenvío masivo de órdenes al servicio I200 (Apigee) corrigiendo el Store F001 a partir de un Excel")
public class ReprocesoNodeController {
    private final ReprocesoNodeService reprocesoNodeService;
    private final ExcelService excelService;

    @Operation(summary = "Reprocesar órdenes corrigiendo el Store contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber). Por cada fila, si algún " +
                    "OrderLine tiene \"Store\": \"F001\" lo reemplaza por \"001\" y envía la orden una por una al servicio I200 " +
                    "de Apigee, espaciando las llamadas (1 s entre envíos y 4 s cada 10). Las órdenes que no contienen F001 no se " +
                    "envían y se marcan como \"No F001\". Devuelve un .xlsx (descarga) con columnas: Request Original, " +
                    "TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarReproceso(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=TrackingNumber") @RequestParam("file") MultipartFile file) throws IOException {
        if (isNotExcelFile(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_Node.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReprocesoNode(
                                reprocesoNodeService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }

    private boolean isNotExcelFile(String fileName) {
        return fileName == null || !(fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
    }
}
