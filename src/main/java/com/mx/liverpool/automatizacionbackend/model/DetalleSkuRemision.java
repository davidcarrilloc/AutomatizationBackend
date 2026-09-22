package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class DetalleSkuRemision {
    private String remision;
    private String skuId;
    private String displayName;
    private BigDecimal totalSku;
    private String customerEmail;
}
