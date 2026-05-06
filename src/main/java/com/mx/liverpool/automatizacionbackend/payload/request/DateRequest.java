package com.mx.liverpool.automatizacionbackend.payload.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DateRequest {
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
}
