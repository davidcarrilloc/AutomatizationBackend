package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ConteoValidacionSl {
    private String fecha;
    private Integer existen;
    private Integer noExisten;
    private Integer historico;
}
