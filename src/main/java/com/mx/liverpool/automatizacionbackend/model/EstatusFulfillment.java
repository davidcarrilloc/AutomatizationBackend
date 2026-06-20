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
public class EstatusFulfillment {
    private String jobId;
    private String estatus;
    private Integer totalTrackings;
    private Integer procesados;
    private Integer conErrorGateway;
    private Integer reprocesados;
    private String trackingActual;
    private LocalDateTime inicio;
    private LocalDateTime fin;
}
