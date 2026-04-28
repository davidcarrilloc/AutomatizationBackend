package com.mx.liverpool.automatizacionbackend.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class OrderRequest {
    @JsonProperty("OrderNo")
    private String orderNo;

    @JsonProperty("EnterpriseCode")
    private String enterpriseCode;

    @JsonProperty("DocumentType")
    private String documentType;
}
