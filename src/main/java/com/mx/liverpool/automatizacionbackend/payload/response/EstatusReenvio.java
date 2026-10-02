package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EstatusReenvio {
    private String estatus;
    private String fechaActual;
    private Integer diasProcesados;
    private Integer diasTotales;
    private Integer totalDia;
    private Integer procesadasDia;
    private String remisionActual;
    private Integer success;
    private Integer failure;
    private Integer error;
    private Integer otro;
    private LocalDateTime inicio;
    private LocalDateTime fin;
    private String mensaje;
}
