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
@Tag(name = "Extracto BRIDGECORE a SFTP", description = "Generación y depósito por SFTP del extracto diario de remisiones de Liverpool")
public class ExtractoBcController {

    private final ExtractoBcService extractoBcService;

    @Operation(summary = "Generar y depositar el extracto de un rango de fechas",
            description = "Consulta BRIDGECORE en el rango indicado y deposita por SFTP un archivo .csv de una sola " +
                    "columna, sin encabezado, solo de SoftLine (id_tipo_articulo = '1'): " +
                    "liverpool_sl_<yyyyMMdd>.csv con las remisiones de Liverpool. Ni Suburbia ni Big Ticket se " +
                    "envían. El sello del nombre es la fecha del inicio del rango, sin hora. Si la consulta no " +
                    "devuelve filas el archivo se deposita vacío. Es el mismo proceso que corre automáticamente " +
                    "todos los días a las 12:00 sobre el día anterior completo; este endpoint sirve para reprocesar " +
                    "un día perdido.")
    @ApiResponse(responseCode = "200", description = "Resumen con el nombre del archivo y el número de líneas depositadas, o el error si la subida falló")
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
