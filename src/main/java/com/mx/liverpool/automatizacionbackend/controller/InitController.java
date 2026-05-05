package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.SQLiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/sql")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class InitController {
    private final SQLiteService sqLiteService;

    @PostMapping(value = "/crearDB")
    public ResponseEntity<?> verificarEnOMSOrdenVenta() {
        return ResponseEntity.ok(sqLiteService.crearTabla());
    }
}
