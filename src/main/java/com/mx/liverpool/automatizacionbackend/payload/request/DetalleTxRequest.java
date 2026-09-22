package com.mx.liverpool.automatizacionbackend.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DetalleTxRequest {
    @NotBlank(message = "El campo atgOrderId no puede estar vacío")
    private String atgOrderId;

    @NotEmpty(message = "El campo atgShippingGroupIds no puede estar vacío")
    private List<@NotBlank String> atgShippingGroupIds;

    @NotBlank(message = "El campo source no puede estar vacío")
    private String source;
}
