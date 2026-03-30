package com.mx.liverpool.automatizacionbackend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AtgMarketplace {
    private String remision;
    private String idCybersource;
    private String atgOrderId;
    private String atgShipGrpId;
    private String totalCobrado;
    private String skuId;
    private String cantidad;
    private String total;
    private String fechaTxCompra;
    private String metodoPago;
    private String autorizacionBancaria;
    private String isMkp;
    private String noTarjeta;
}
