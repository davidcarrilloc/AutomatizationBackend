package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ConsultaBcService;
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
@RequestMapping("/api/v1/consulta-bc")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Consulta Bridgecore", description = "Permite consultar más de mil registros por tracking number, orden de venta o shipping group")
public class ConsultaBcController {
    private final ConsultaBcService consultaBcService;
    private final ExcelService excelService;

    @Operation(summary = "Consultar transacciones no enviadas a OMS por remisión",
            description = "Recibe un Excel de una columna (A: remisiones) y devuelve, por cada remisión, sus transacciones " +
                    "no enviadas a OMS (id_cat_estatus = 0, id_tipo_tx = 1) desde BRIDGECORE. Las remisiones se consultan " +
                    "en lotes de 1000 (límite del IN de Oracle) y los resultados se concatenan. Devuelve un .xlsx (descarga) " +
                    "con el volcado de 46 columnas de tx_informacion_procesada.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el volcado de transacciones no enviadas a OMS")
    @PostMapping(value = "/trackingnumber", consumes = {"multipart/form-data"})
    public ResponseEntity<?> consultarNoOms(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de una columna: A=remisiones") @RequestParam("file") MultipartFile file) throws IOException {
        validarExcel(file);
        return descargar(consultaBcService.consultarNoOms(file), "Reporte_BC.xlsx");
    }

    @Operation(summary = "Consultar transacciones no enviadas a OMS por orden de venta",
            description = "Igual que /trackingnumber pero la columna A trae órdenes de venta (tip.orden_venta). Lotes de 1000. " +
                    "Devuelve el mismo .xlsx (descarga) de 46 columnas de tx_informacion_procesada.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el volcado de transacciones no enviadas a OMS")
    @PostMapping(value = "/trackingnumber/orden-venta", consumes = {"multipart/form-data"})
    public ResponseEntity<?> consultarNoOmsPorOrdenVenta(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de una columna: A=órdenes de venta") @RequestParam("file") MultipartFile file) throws IOException {
        validarExcel(file);
        return descargar(consultaBcService.consultarNoOmsPorOrdenVenta(file), "Reporte_BC_OrdenVenta.xlsx");
    }

    @Operation(summary = "Consultar transacciones no enviadas a OMS por shipping group",
            description = "Igual que /trackingnumber pero la columna A trae shipping groups de ATG (tip.atg_ship_grp_id). Lotes de 1000. " +
                    "Devuelve el mismo .xlsx (descarga) de 46 columnas de tx_informacion_procesada.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el volcado de transacciones no enviadas a OMS")
    @PostMapping(value = "/trackingnumber/shipping-group", consumes = {"multipart/form-data"})
    public ResponseEntity<?> consultarNoOmsPorShippingGroup(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de una columna: A=shipping groups") @RequestParam("file") MultipartFile file) throws IOException {
        validarExcel(file);
        return descargar(consultaBcService.consultarNoOmsPorShippingGroup(file), "Reporte_BC_ShippingGroup.xlsx");
    }

    private void validarExcel(MultipartFile file) {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");
    }

    private ResponseEntity<?> descargar(byte[] reporte, String nombreArchivo) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nombreArchivo);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(reporte);
    }
}
