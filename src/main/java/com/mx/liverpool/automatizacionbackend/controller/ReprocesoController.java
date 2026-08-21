package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoBillToService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoFacadeService;
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
@RequestMapping("/api/v1/reproceso")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Reproceso", description = "Reenvío masivo de órdenes al servicio I200 (Apigee) a partir de un Excel")
public class ReprocesoController {
    private final ReprocesoFacadeService reprocesoFacadeService;
    private final ReprocesoNodeService reprocesoNodeService;
    private final ReprocesoBillToService reprocesoBillToService;
    private final ExcelService excelService;

    @Operation(summary = "Reprocesar órdenes contra I200",
            description = "Recibe un Excel de tres columnas (A: JSON del pedido, B: remisión, C: ItemID). Por cada fila " +
                    "reemplaza el ItemID del JSON con el valor de la columna C y envía la orden una por una al servicio I200 " +
                    "de Apigee, espaciando las llamadas (1 s entre envíos y 4 s cada 10). Devuelve un .xlsx (descarga) con " +
                    "columnas: Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/facade/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarFacade(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de tres columnas: A=JSON, B=remisión, C=ItemID") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_Facade.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoFacadeService.reprocesar(
                                        excelService.leerReprocesoFacade(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes corrigiendo el Store contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber). Por cada fila, si algún " +
                    "OrderLine tiene \"Store\": \"F001\" lo reemplaza por \"001\" y envía la orden una por una al servicio I200 " +
                    "de Apigee, espaciando las llamadas (1 s entre envíos y 4 s cada 10). Las órdenes que no contienen F001 no se " +
                    "envían y se marcan como \"No F001\". Devuelve un .xlsx (descarga) con columnas: Request Original, " +
                    "TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/node/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarNode(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=TrackingNumber") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_Node.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoNodeService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes rellenando PersonInfoBillTo contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber). Por cada fila copia a " +
                    "\"PersonInfoBillTo\" los campos de \"PersonInfoShipTo\" que el BillTo trae vacíos (ausentes, nulos, vacíos " +
                    "o con solo espacios), respetando los que ya tienen valor propio y saltando las extensiones LExtn. Si tras " +
                    "el merge queda vacío algún campo obligatorio del INT200 la orden no se envía y se reporta cuál falta; los " +
                    "obligatorios con valor por omisión (Country, AddressLine3) se rellenan y sí se envían. Las órdenes que se " +
                    "envían van una por una al servicio I200 de Apigee, espaciando las llamadas (1 s entre envíos y 4 s cada 10). " +
                    "Devuelve un .xlsx (descarga) con columnas: Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/billto/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarBillTo(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=TrackingNumber") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_BillTo.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoBillToService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }
}
