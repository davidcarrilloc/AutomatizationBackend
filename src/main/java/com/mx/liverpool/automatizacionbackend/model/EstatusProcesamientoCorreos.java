package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EstatusProcesamientoCorreos {
    private String jobId;
    private String estatus;
    private String carpetaSalida;
    private Integer totalSegmentos;
    private Integer segmentosProcesados;
    private Integer segmentosOmitidos;
    private Long totalRegistros;
    private String segmentoActual;
    private List<String> errores;
    private LocalDateTime inicio;
    private LocalDateTime fin;
}
