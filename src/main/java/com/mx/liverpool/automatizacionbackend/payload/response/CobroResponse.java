package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
public class CobroResponse {
    private String bcTransactionId;
    private String noPedido;
    private String numeroRemision;
    private String ordenVenta;
    private String recognitionStore;
    private Double montoTotal;
    private Boolean estadoTransaccion;
    private Integer codigoRetorno;
    private String terminal;
    private Integer numeroSkus;
    private Date fechaTxCompra;
    // private String nombreUsuario;
    // private String customerEmail;
    // private String atgShipGrpId;
    // private String tipoArticulo;
    // private String atgOrderId;
    private Double montoAbonoMed;
    private Double montoCobroMed;
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
