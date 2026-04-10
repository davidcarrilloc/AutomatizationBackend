package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ItemsResponse {
    private Long idSku;
    private Integer cantidad;
    private Double descuentoCasa;
    private Double descuentoFijo;
    private Double descuentoPorcentual;
    private String idPromo;
    private Double importeTotal;
    private Boolean isGift;
    private Integer noSeccion;
    private Double precioLista;
    private Double precioVenta;
    private Boolean promoMed;
    private Integer promoMedType;
    private Double promoMedValue;
    private String skuDescription;
    private Double totalDescuento;
    private Boolean flete;
}
