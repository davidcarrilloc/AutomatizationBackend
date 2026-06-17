package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.payload.request.DetalleTxRequest;
import com.mx.liverpool.automatizacionbackend.service.TxService;
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
public class TxController {
    private final TxService txService;

    @PostMapping("/detalleTx")
    public ResponseEntity<?> detalleTx(@RequestBody DetalleTxRequest request) {
        log.info("Entrando a detalleTx");
        return ResponseEntity.ok(
                txService.obtenerDetalleTx(request.getAtgOrderId(), request.getAtgShippingGroupId(), request.getSource())
        );
    }

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
