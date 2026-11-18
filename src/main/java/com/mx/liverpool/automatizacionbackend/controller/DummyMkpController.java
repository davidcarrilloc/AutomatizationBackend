package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.DummyMkpService;
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
@RequestMapping("/api/v1/dummy-mkp")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Dummy MKP", description = "Envío de dummies de marketplace a Entrada Única y su cancelación inmediata para devolver el dinero al cliente")
public class DummyMkpController {
    private final DummyMkpService dummyMkpService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar proceso de dummies",
            description = "Recibe un Excel de dos columnas (A: remisión, B: offerId) e inicia un job asíncrono. Por cada fila " +
                    "consulta Bridgecore, arma una fila dummy por SKU sobre la plantilla, genera su JSON, lo envía a Entrada " +
                    "Única y cancela la orden. Las filas sin offerId no aplican. El Excel de dummies y los JSON quedan en " +
                    "la carpeta del job (dummy-mkp.directorio-salida/<jobId>). Devuelve el jobId.")
    @ApiResponse(responseCode = "202", description = "Proceso aceptado; devuelve el jobId")
    @PostMapping(value = "/dummies", consumes = {"multipart/form-data"})
    public ResponseEntity<?> iniciarDummies(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=remisión, B=offerId") @RequestParam("file") MultipartFile file) {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        return ResponseEntity.accepted().body(dummyMkpService.iniciarProceso(excelService.leerDummyMkp(file)));
    }

    @Operation(summary = "Consultar estatus del proceso de dummies",
            description = "Devuelve el estatus del job. Mientras arma, totalTrackings son las filas del Excel; mientras envía, " +
                    "son los dummies a enviar. trackingActual indica la fase y la remisión. conErrorGateway cuenta las filas con error.")
    @ApiResponse(responseCode = "200", description = "Estatus del job de dummies")
    @GetMapping("/dummies/estatus/{jobId}")
    public ResponseEntity<?> obtenerEstatusDummies(
            @Parameter(description = "Identificador del job de dummies", example = "1754500000000") @PathVariable String jobId) {
        return ResponseEntity.ok(dummyMkpService.obtenerEstatus(jobId));
    }

    @Operation(summary = "Listar los jobs de dummies",
            description = "Devuelve todos los jobs de dummies desde el último arranque de la aplicación, del más reciente al más viejo.")
    @ApiResponse(responseCode = "200", description = "Lista de jobs con su avance")
    @GetMapping("/dummies/jobs")
    public ResponseEntity<?> obtenerJobsDummies() {
        return ResponseEntity.ok(dummyMkpService.obtenerJobs());
    }

    @Operation(summary = "Descargar reporte de dummies",
            description = "Genera un .xlsx (descarga) con columnas Remisión, OfferId, Error y, si no hubo error, ORDER_ID, " +
                    "SHIPPING_GROUP_ID, SKU, Cantidad, Total, Archivo JSON, Respuesta envío y Respuesta cancelación.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado de cada dummy")
    @GetMapping("/dummies/excel/{jobId}")
    public ResponseEntity<?> obtenerExcelDummies(
            @Parameter(description = "Identificador del job de dummies", example = "1754500000000") @PathVariable String jobId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Dummies_" + jobId + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.crearReporteDummyMkp(dummyMkpService.obtenerResultados(jobId)));
    }
}
