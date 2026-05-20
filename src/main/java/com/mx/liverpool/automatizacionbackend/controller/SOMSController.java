package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.SOMSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/soms")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
public class SOMSController {
    private final SOMSService somsService;

    @PostMapping("/reporte/remisiones-sin-datos")
    public ResponseEntity<?> detalleTx() {
        return ResponseEntity.ok(somsService.obtenerReporte());
    }
}
