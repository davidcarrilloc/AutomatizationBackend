package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ReenvioBitacora {
    private String fecha;
    private Integer total;
    private Integer success;
    private Integer failure;
    private Integer error;
    private Integer otro;
    private String inicio;
    private String fin;
}
