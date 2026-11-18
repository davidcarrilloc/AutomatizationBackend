package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class DummyMkpResult {
    private String remision;
    private String offerId;
    private String error;
    private String orderId;
    private String shippingGroupId;
    private String sku;
    private String cantidad;
    private String total;
    private String archivoJson;
    private String respuestaEnvio;
    private String respuestaCancelacion;
}
