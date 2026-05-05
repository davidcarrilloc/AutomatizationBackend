package com.mx.liverpool.automatizacionbackend.model;

import lombok.Data;

@Data
public class TxPorMinuto {
    private String canal;
    private String sitio;
    private String truncdate;
    private Integer transacciones;
    private Double totalCobrado;
}
