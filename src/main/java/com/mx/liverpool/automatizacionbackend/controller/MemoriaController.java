package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.MemoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/memoria")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Liberar memoria", description = "Libera la RAM de los procesos asíncronos cuando se quedan trabados, sin reiniciar el server")
public class MemoriaController {
    private final MemoriaService memoriaService;

    @Operation(summary = "Liberar la memoria de los procesos asíncronos",
            description = "Borra de memoria todos los jobs (estatus y resultados) de Fulfillment, Reproceso Completo, Availability, " +
                    "Fachada y Fulfillment TXT, y deja el Reenvío en SIN_INICIAR. Los jobs en curso se cortan al terminar su llamada actual " +
                    "y los encolados salen sin procesar. La bitácora y resultados del Reenvío en SQLite no se tocan. " +
                    "Una llamada HTTP que nunca responde sigue ocupando su hilo. No requiere parámetros. Irreversible.")
    @ApiResponse(responseCode = "200", description = "Jobs liberados por proceso y estatus final del Reenvío")
    @PostMapping("/liberar")
    public ResponseEntity<?> liberarMemoria() {
        return ResponseEntity.ok(memoriaService.liberar());
    }
}
