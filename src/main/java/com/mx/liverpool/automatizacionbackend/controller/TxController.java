package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.payload.request.DetalleTxRequest;
import com.mx.liverpool.automatizacionbackend.service.TxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tx")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "TX", description = "Detalle y comparativa de transacciones")
public class TxController {
    private final TxService txService;

    @Operation(summary = "Detalle de una transacción",
            description = "Recibe atgOrderId, atgShippingGroupId y source en el cuerpo y devuelve el detalle de la transacción correspondiente.")
    @ApiResponse(responseCode = "200", description = "Detalle de la transacción consultada")
    @PostMapping("/detalleTx")
    public ResponseEntity<?> detalleTx(@RequestBody DetalleTxRequest request) {
        log.info("Entrando a detalleTx");
        return ResponseEntity.ok(
                txService.obtenerDetalleTx(request.getAtgOrderId(), request.getAtgShippingGroupId(), request.getSource())
        );
    }

    @Operation(summary = "Reporte comparativo TX hoy vs. ayer",
            description = "Genera un .xlsx (descarga) que compara el volumen de transacciones por hora del día actual contra el día anterior. No requiere parámetros.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con la comparativa de TX")
    @GetMapping("/reporte/diferencia")
    public ResponseEntity<?> obtenerDiferenciaTx() {
        log.info("Entrando a obtenerDiferenciaTx");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Comparativa_TX.xlsx");
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(txService.obtenerDiferenciaTxHoyvsAyer());
    }
}
