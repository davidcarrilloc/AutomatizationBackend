package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DireccionCopomexResponse {
    private String codigoPostal;
    private String colonia;
    private String municipio;
    private String ciudad;
    private String estado;
    private String calle;
    private String numeroExterior;
    private String numeroInterior;
}
