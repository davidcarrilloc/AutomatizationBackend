package com.mx.liverpool.automatizacionbackend.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BC {
    private String remision;
    private String totalCobrado;
    private String atgOrderId;
    private String atgShipGrpId;
    private String diferencia;
    private LocalDateTime fechaTxCompra;
}
