package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ReenvioResultado {
    private String fecha;
    private String remision;
    private String origen;
    private String categoria;
    private String response;
    private String json;
}
