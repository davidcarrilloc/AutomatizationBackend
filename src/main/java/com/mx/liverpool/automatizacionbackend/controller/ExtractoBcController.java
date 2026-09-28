package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExtractoBcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/extracto-bc")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Extracto BRIDGECORE a SFTP", description = "Generación y depósito por SFTP del extracto diario OMS: SoftLine (Liverpool + Suburbia) y Big Ticket")
public class ExtractoBcController {

    private final ExtractoBcService extractoBcService;

    @Operation(summary = "Generar y depositar el extracto de un rango de fechas",
            description = "Consulta BRIDGECORE en el rango indicado y deposita por SFTP dos archivos .csv de una " +
                    "sola columna, sin encabezado: oms_sl_<yyyyMMdd>.csv, que junta las remisiones de Liverpool " +
                    "SoftLine (id_tipo_articulo = '1') seguidas de los shipping groups de Suburbia (BRIDGECORE2); " +
                    "y oms_bt_<yyyyMMdd>.csv con las órdenes de venta de Big Ticket (id_tipo_articulo = '0'). " +
                    "El sello del nombre es la fecha del inicio del rango, sin hora. Entrega parcial: cada consulta " +
                    "va en su propio intento y oms_sl se deposita con lo que sí salió (si Suburbia falla pero " +
                    "Liverpool no, sube solo Liverpool); si todas las fuentes de un archivo fallan, ese archivo no " +
                    "se sube. Los errores por fuente quedan en bitácora. Si una consulta no devuelve filas su " +
                    "archivo se deposita vacío. Es el mismo proceso que corre automáticamente todos los días a las " +
                    "12:00 sobre el día anterior completo; este endpoint sirve para reprocesar un día perdido.")
    @ApiResponse(responseCode = "200", description = "Resumen JSON con una llave por archivo (oms_sl_…, oms_bt_…) con el número de líneas subidas, el error o si se omitió")
    @PostMapping("/enviar")
    public ResponseEntity<?> enviarExtracto(
            @Parameter(description = "Inicio del rango de FECHA_TX_COMPRA, formato ISO", example = "2026-09-17T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @Parameter(description = "Fin del rango de FECHA_TX_COMPRA, formato ISO", example = "2026-09-17T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin) {
        if (fin.isBefore(inicio)) throw new IllegalArgumentException("La fecha fin no puede ser anterior a la fecha inicio.");
        return ResponseEntity.ok(extractoBcService.generarYEnviar(inicio, fin));
    }
}
