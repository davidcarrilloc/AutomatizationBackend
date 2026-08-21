package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.ValidadorService;
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
@RequestMapping("/api/v1/validador")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Validador", description = "Validación de órdenes contra el contrato INT200 antes de enviarlas a OMS/SOMS")
public class ValidadorController {
    private final ValidadorService validadorService;
    private final ExcelService excelService;

    @Operation(summary = "Validar órdenes contra el INT200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: remisión). Por cada fila revisa el JSON " +
                    "contra las reglas del INT200 (src/main/resources/int200-rules.json): campos obligatorios ausentes, " +
                    "valores fuera de catálogo, longitudes excedidas, mojibake y campos que la definición pide vacíos. " +
                    "Tolera el JSON con o sin la llave envolvente \"Order\". No envía nada a Apigee: solo diagnostica. " +
                    "Devuelve un .xlsx (descarga) con columnas: JSON, Remisión, Validaciones y Errores.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado de la validación")
    @PostMapping(value = "/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> validarOrdenes(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=remisión") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Validacion_INT200.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteValidacion(
                                validadorService.validar(
                                        excelService.leerValidacion(file)
                                )
                        )
                );
    }

    @Operation(summary = "Extraer Remisión / OfferId / Sku de los REQUEST de marketplace",
            description = "Recibe un Excel de dos columnas (A: REQUEST enviado a Entrada Única, B: tracking number). " +
                    "De cada REQUEST extrae la remisión (\"commercial_id\") y, por cada elemento de \"offers\", el " +
                    "\"offer_id\" y el sku (el \"value\" de order_line_additional_fields cuyo \"code\" es " +
                    "\"product-sap-sku-id\"). Una fila de entrada puede producir varios registros de salida: si un " +
                    "REQUEST trae 3 offers, se escriben 3 filas con la misma remisión y distinto offerId/sku. " +
                    "El tracking number solo se usa para trazar la fila en bitácora, no sale en el reporte. " +
                    "Devuelve un .xlsx (descarga) con columnas: Remisión, OfferId, Sku y Errores.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con un registro por relación remisión-offer-sku")
    @PostMapping(value = "/marketplace", consumes = {"multipart/form-data"})
    public ResponseEntity<?> extraerMarketplace(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=REQUEST JSON, B=tracking number") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Validador_Marketplace.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteMarketplace(
                                validadorService.extraerMarketplace(
                                        excelService.leerMarketplace(file)
                                )
                        )
                );
    }
}
