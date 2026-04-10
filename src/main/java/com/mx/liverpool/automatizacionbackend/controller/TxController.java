package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.payload.request.DetalleTxRequest;
import com.mx.liverpool.automatizacionbackend.service.TxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
}
