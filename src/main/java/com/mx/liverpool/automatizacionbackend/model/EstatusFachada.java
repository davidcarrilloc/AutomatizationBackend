package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EstatusFachada {
    private String jobId;
    private String estatus;
    private Integer total;
    private Integer procesados;
    private Integer errores;
    private Integer bloques;
    private LocalDateTime inicio;
    private LocalDateTime fin;
}
