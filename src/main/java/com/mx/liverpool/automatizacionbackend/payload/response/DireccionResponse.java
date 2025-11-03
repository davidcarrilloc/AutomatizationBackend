package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DireccionResponse{
    private String calle;
    private String numeroExterior;
    private String numeroInterior;
    private String codigoPostal;
    private String colonia;
    private String municipio;
    private String estado;
    private String pais;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
}