package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
public class CobroResponse {
    private Integer bcTransactionId;
    private Integer certificado;
    private String noPedido;
    private Long numeroRemision;
    // private Long ordenVenta;
    private String recognitionStore;
    private Double montoTotal;
    private Boolean estadoTransaccion;
    private String codigoRetorno;
    private Integer terminal;
    private Integer numeroSkus;
    // private Date fechaTxCompra;
    // private String nombreUsuario;
    // private String customerEmail;
    // private String atgShipGrpId;
    // private String tipoArticulo;
    // private String atgOrderId;
    private Double montoAbonoMed;
    private Double montoCobroMed;
    private Double descuentoAplicado;
    private String mensaje;
    private String noAutorizacion;
    private Boolean paqueteriaOffLine;
    private Boolean empleado;
    private Boolean descuentoDe1erDiaAplicado;
    private List<ItemsResponse> items;
}

/*
select tx.id, -- bcTransactionId
tip.pedido, -- noPedido
tip.remision, -- numeroRemision
tip.orden_venta, -- numeroOrdenVenta
tip.recognition_store, -- recognitionStore
tip.total_cobrado, -- montoTotal
tip.id_cat_estatus, -- estadoTransaccion
tip.terminal, -- terminal
TX.TOTAL_SKUS, -- numeroSkus
TIP.FECHA_TX_COMPRA,
CTE.NOMBRE_USUARIO,
CTE.CUSTOMER_EMAIL,
TIP.atg_ship_grp_id orden_venta,
ta.art_descripcion tipo_articulo,
SK.SECCION,
TIP.RECOGNITION_STORE "nodo_envio",
tip.pedido,
tip.atg_order_id,
DMA.MONTO abono_med, -- montoAbonoMed
DMC.MONTO cargo_med -- montoCobroMed
*/
