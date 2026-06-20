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
public class OmsFaltante {
    private String atgOrderId;
    private String atgShipGrpId;
    private LocalDateTime fechaTxCompra;
    private String diferencia;
    private String errorDetail;
    private Integer idTipoTx;
    private String ordenVenta;
    private Long id;
    private Integer idCatEstatus;
    private String pedido;
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
