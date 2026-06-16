package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class OrdenSoms {
    private String remision;
    private String statusDatos;
    private String nodoDestinatario;
    private String response;
}
