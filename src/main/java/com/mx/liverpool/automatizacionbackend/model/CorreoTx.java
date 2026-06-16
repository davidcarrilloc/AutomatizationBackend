package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class CorreoTx {
    private String errorDetail;
    private Integer idTipoTx;
    private String atgShipGrpId;
    private String atgOrderId;
    private String ordenVenta;
    private String customerEmail;
    private Long id;
    private Integer idCatEstatus;
    private String pedido;
    private LocalDateTime fechaTxCompra;
    private String boleta;
    private String terminal;
    private String remision;
    private BigDecimal totalCobrado;
    private BigDecimal totalOriginal;
    private String isMkp;
    private String zipCode;
    private String recognitionStore;
    private String recognitionStoreChannel;
    private String recognitionStoreSubChannel;
    private String tiendaCliente;
}
