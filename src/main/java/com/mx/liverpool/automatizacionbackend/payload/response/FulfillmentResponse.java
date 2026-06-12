package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class FulfillmentResponse {
    private String statusSoms;
    private String statusMkp;
    private String statusSterling;
    private String statusPendingOrder;
    private String statusOms;
    private String statusProtec;
    private String statusMyPurchases;
    private String statusFirebase;
    private String statusPreBackOrder;
    private String statusEmail;
    private String error;
    private String trackingNumber;
    private String orderId;
}
