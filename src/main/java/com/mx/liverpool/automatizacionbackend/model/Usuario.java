package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Usuario {
    private Long id;

    private String nombre;

    private String apellidoPaterno;

    private String apellidoMaterno;

    private String correo;

    private Direccion direccion;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaActualizacion;
}
