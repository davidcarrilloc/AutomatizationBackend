package com.mx.liverpool.automatizacionbackend.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DetalleTxRequest {
    @NotBlank(message = "El campo atgOrderId no puede estar vacío")
    private String atgOrderId;

    private CredentialsTxRequest credentials;

    @NotBlank(message = "El campo atgShippingGroupId no puede estar vacío")
    private String atgShippingGroupId;

    @NotBlank(message = "El campo source no puede estar vacío")
    private String source;
}
