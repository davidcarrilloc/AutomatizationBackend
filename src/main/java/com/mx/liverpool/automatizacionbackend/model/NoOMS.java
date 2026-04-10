package com.mx.liverpool.automatizacionbackend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString
public class NoOMS {
    private String msgDt;
    private String responseStatus;
    private String orderSiteId;
    private String sitio;
    private int total;
}
