package com.mx.liverpool.automatizacionbackend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CodigoDigital {
    private String refTransId;
    private String codigo;
    private String descripcion;
    private LocalDateTime creationDate;
    private String requestType;
    private String endpoint;
    private String requestBody;
    private String responseBody;
}
