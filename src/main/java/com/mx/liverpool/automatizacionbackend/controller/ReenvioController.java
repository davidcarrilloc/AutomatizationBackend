package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.ReenvioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reenvio")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Reenvío fulfillment", description = "Reenvío masivo al fulfillment de las cobradas LP BT, LP SL y SBB, día por día desde ayer hasta reenvio.fecha-limite, con bitácora en SQLite")
public class ReenvioController {
    private final ReenvioService reenvioService;
    private final ExcelService excelService;

    @Operation(summary = "Iniciar o detener el reenvío",
            description = "Funciona como play/pausa. Si no hay corrida, recorre los días desde ayer hasta reenvio.fecha-limite " +
                    "saltando los que ya están en la bitácora. Si hay una corrida en proceso, la detiene al terminar la remisión en curso " +
                    "(el día a medias no se marca; al reanudar continúa con las remisiones que aún no tienen resultado). No requiere parámetros.")
    @ApiResponse(responseCode = "202", description = "Estatus de la corrida: EN_PROCESO, DETENIENDO o COMPLETADO si no hay días pendientes")
    @PostMapping("/ejecutar")
    public ResponseEntity<?> ejecutarReenvio() {
        return ResponseEntity.accepted().body(reenvioService.ejecutar());
    }

    @Operation(summary = "Reiniciar el reenvío",
            description = "Detiene la corrida en proceso, si la hay, y espera a que suelte la remisión en curso. Después arranca desde ayer " +
                    "hacia reenvio.fecha-limite saltando los días que ya están en la bitácora. No requiere parámetros.")
    @ApiResponse(responseCode = "202", description = "Estatus de la nueva corrida: EN_PROCESO o COMPLETADO si no hay días pendientes")
    @PostMapping("/reiniciar")
    public ResponseEntity<?> reiniciarReenvio() {
        return ResponseEntity.accepted().body(reenvioService.reiniciar());
    }

    @Operation(summary = "Consultar el avance del reenvío",
            description = "Devuelve estatus (SIN_INICIAR, EN_PROCESO, DETENIENDO, DETENIDO, COMPLETADO, ERROR), día actual, días procesados/totales, " +
                    "remisiones del día procesadas/totales, remisión actual y conteos SUCCESS/FAILURE/ERROR/OTRO acumulados. " +
                    "Los conteos salen de la base (se conservan entre corridas y reinicios; solo /limpiar los baja). " +
                    "El avance de la corrida vive en memoria: tras reiniciar la aplicación sale SIN_INICIAR.")
    @ApiResponse(responseCode = "200", description = "Estatus actual del reenvío")
    @GetMapping("/estatus")
    public ResponseEntity<?> obtenerEstatusReenvio() {
        return ResponseEntity.ok(reenvioService.obtenerEstatus());
    }

    @Operation(summary = "Limpiar información del reenvío",
            description = "Detiene la corrida en proceso, si la hay, y borra lo indicado en tipos (uno o varios): SUCCESS, FAILURE, ERROR u OTRO " +
                    "quitan esos resultados del Excel y de los conteos; DIAS vacía la bitácora para que los días se vuelvan a recorrer; TODO borra todo. " +
                    "Al recorrer un día se saltan las remisiones que siguen con resultado, así que DIAS + FAILURE reenvía solo las FAILURE. Irreversible.")
    @ApiResponse(responseCode = "200", description = "Estatus tras limpiar, con el detalle de lo borrado en mensaje")
    @PostMapping("/limpiar")
    public ResponseEntity<?> limpiarReenvio(
            @Parameter(description = "Qué limpiar; uno o varios",
                    array = @ArraySchema(schema = @Schema(allowableValues = {"SUCCESS", "FAILURE", "ERROR", "OTRO", "DIAS", "TODO"})))
            @RequestParam List<String> tipos) {
        return ResponseEntity.ok(reenvioService.limpiar(tipos));
    }

    @Operation(summary = "Consultar la bitácora del reenvío",
            description = "Devuelve los días ya procesados, del más reciente al más viejo, con total de remisiones, conteos por categoría, inicio y fin.")
    @ApiResponse(responseCode = "200", description = "Días procesados")
    @GetMapping("/bitacora")
    public ResponseEntity<?> obtenerBitacoraReenvio() {
        return ResponseEntity.ok(reenvioService.obtenerBitacora());
    }

    @Operation(summary = "Descargar resultados del reenvío",
            description = "Genera un .xlsx (descarga) con Fecha, Remisión, Origen (LP_SL, LP_BT, SBB_TRACKING, SBB_SG), Categoría (SUCCESS, FAILURE, ERROR, OTRO), " +
                    "Response y JSON de los días del rango. Sin fechas descarga todo. Responde 400 si el rango excede 1 048 575 filas.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con los resultados del rango")
    @GetMapping("/excel")
    public ResponseEntity<?> obtenerExcelReenvio(
            @Parameter(description = "Fecha inicial del rango, inclusive (yyyy-MM-dd)", example = "2026-09-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @Parameter(description = "Fecha final del rango, inclusive (yyyy-MM-dd)", example = "2026-09-28")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reenvio_"
                + (fechaInicio == null ? "inicio" : fechaInicio) + "_" + (fechaFin == null ? "fin" : fechaFin) + ".xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.crearReporteReenvio(reenvioService.obtenerResultados(fechaInicio, fechaFin)));
    }
}
