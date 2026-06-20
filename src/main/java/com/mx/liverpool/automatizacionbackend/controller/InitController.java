package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.SQLiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/sql")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name = "SQL / Init", description = "Inicialización del almacén local SQLite")
public class InitController {
    private final SQLiteService sqLiteService;

    @Operation(summary = "Crear base de datos SQLite",
            description = "Crea la tabla de métricas de transacciones en el almacén local SQLite. No requiere parámetros.")
    @ApiResponse(responseCode = "200", description = "Resultado de la creación de la tabla")
    @PostMapping(value = "/crearDB")
    public ResponseEntity<?> verificarEnOMSOrdenVenta() {
        return ResponseEntity.ok(sqLiteService.crearTabla());
    }
}
