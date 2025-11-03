package com.mx.liverpool.automatizacionbackend.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DireccionRequest {
    /**
     * Copomex solo entrega informacion si tu tienes
     * codigoPostal, colonia, municipio y calle.
     */
    @Pattern(regexp = "\\d{5}", message = "El CP debe tener 5 dígitos")
    private String codigoPostal;

    @NotBlank(message = "La colonia es obligatoria")
    private String colonia;

    @NotBlank(message = "La calle es obligatoria")
    private String calle;

    @NotBlank(message = "El número exterior es obligatorio")
    private String numeroExterior;

    private String numeroInterior;

    /**
     * Rellenados por Copomex
    private String municipio;
    private String estado;
    private String pais;
     */
}
