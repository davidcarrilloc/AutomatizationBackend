package com.mx.liverpool.automatizacionbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.mx.liverpool.automatizacionbackend.payload.request.UsuarioRequest;
import com.mx.liverpool.automatizacionbackend.service.UsuarioService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<?> obtenerUsuarios(
            @ParameterObject
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable,
            @RequestParam(required = false) String nombre) {
        return ResponseEntity.ok(usuarioService.listarUsuarios(pageable, nombre));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerUsuarioPorId(@PathVariable String id) {
        return ResponseEntity.ok(usuarioService.obtenerUsuarioPorId(id));
    }

    @PostMapping
    @Operation(
            summary = "Crear un nuevo usuario",
            description = "Crea un nuevo usuario con la información proporcionada.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Datos del usuario a crear",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UsuarioRequest.class),
                            examples = @ExampleObject(
                                    value = """
                            {
                                "nombre": "María Fernanda",
                                "apellidoPaterno": "López",
                                "apellidoMaterno": "Cruz",
                                "email": "m.fernanda@example.com",
                                "direccionRequest": {
                                    "codigoPostal": "06600",
                                    "colonia": "Juárez",
                                    "calle": "Av. Paseo de la Reforma",
                                    "numeroExterior": "135",
                                    "numeroInterior": "3B"
                                }
                            }"""
                            )
                    )
            )
    )
    public ResponseEntity<?> crearUsuario(@Valid @RequestBody UsuarioRequest usuarioRequest) {
        return ResponseEntity.ok(usuarioService.crearUsuario(usuarioRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarUsuario(@PathVariable String id, @Valid @RequestBody UsuarioRequest usuario) {
        return ResponseEntity.ok(usuarioService.actualizarUsuario(id, usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarUsuario(@PathVariable String id) {
        return ResponseEntity.ok(usuarioService.eliminarUsuario(id));
    }
}
