package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Direccion {
    /**
     * Entidad fuerte para tener historico de direcciones pasadas o si el sistema necesita multiples
     * direcciones p.e. facturacion, envio, casa, trabajo, etc.
     */
    private Long id;

    private String calle;

    private String numeroExterior;

    private String numeroInterior;

    private String codigoPostal;

    private String colonia;

    private String municipio;

    private String estado;

    private String pais = "México";

    private Usuario usuario;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaActualizacion;
}
