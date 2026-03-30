package com.mx.liverpool.automatizacionbackend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Date;

@Data
@NoArgsConstructor
@ToString
public class CobroRow {
    private String id;
    private String pedido;
    private String remision;
    private String ordenVenta;
    private String recognitionStore;
    private Double totalCobrado;
    private Integer idCatEstatus;
    private String terminal;
    private Integer totalSkus;
    private Date fechaTxCompra;
    private String nombreUsuario;
    private String customerEmail;
    private String atgShipGrpId;
    private String tipoArticulo;
    private String skuId;
    private Integer seccion;
    private String displayName;
    private Double precioVenta;
    private Integer cantidad;
    private Double totalSku;
    private String nodoEnvio;
    private String atgOrderId;
    private Double abonoMed;
    private Double cargoMed;
    private Double importeDescto1erDia;
    private Double importeDesctoCasa;
    private Double importeDesctoFijo;
    private Double descuentoPorcentual;
    private Boolean esFlete;
    private String isGift;
}

// IMPORTE_DESCTO_1ERDIA
// IMPORTE_DESCTO_CASA
// IMPORTE_DESCTO_FIJO
// DESCUENTO_PORCENTUAL
// SK.ES_FLETE
// SK.IS_GIFT