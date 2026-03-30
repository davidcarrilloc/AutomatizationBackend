package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ItemsResponse {
    private String idSku;
    private String couponId;
    private Boolean descuentoDe1erDiaAplicado;
    private Double descuentoDe1erDia;
    private Integer cantidad;
    private Double descuentoCasa;
    private Double descuentoFijo;
    private Double descuentoPorcentual;
    private Boolean flete;
    private String idPromo;
    private Double importeTotal;
    private Boolean isGift;
    private Integer noSeccion;
    private Double precioLista;
    private Double precioVenta;
    private Boolean promoMed;
    private String promoMedType;
    private String promoMedValue;
    private String skuDescription;
    private Double totalDescuento;
    private Boolean empleado;
    private Boolean esFlete;
}
