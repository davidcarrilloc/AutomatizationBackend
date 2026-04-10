package com.mx.liverpool.automatizacionbackend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Date;

@Data
@NoArgsConstructor
@ToString
public class CobroRow {
    private Integer id;
    private String pedido;
    private String remision;
    private String ordenVenta;
    private String recognitionStore;
    private Integer seller;
    private Double totalCobrado;
    private Integer idCatEstatus;
    private Integer terminal;
    private Integer totalSkus;
    private Date fechaTxCompra;
    private String nombreUsuario;
    private String customerEmail;
    private String atgShipGrpId;
    private String tipoArticulo;
    private Long skuId;
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
    private Integer esFlete;
    private String isGift;
    private Integer monederoPromoType;
    private Double monederoPromoValue;
    private String autorizacion;
    private Integer certificado;
}

// IMPORTE_DESCTO_1ERDIA
// IMPORTE_DESCTO_CASA
// IMPORTE_DESCTO_FIJO
// DESCUENTO_PORCENTUAL
// SK.ES_FLETE
// SK.IS_GIFT
//SK.MONEDERO_PROMO_TYPE, \
//SK.MONEDERO_PROMO_VALUE, \
//SK.MONEDERO_PROMO_MONTO \
// AUTORIZACION
// bt.CERTIFICADO \
