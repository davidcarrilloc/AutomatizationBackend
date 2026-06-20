package com.mx.liverpool.automatizacionbackend.payload.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DetalleTxRequest {
    @Schema(description = "Identificador de la orden ATG", example = "o12345678")
    @NotBlank(message = "El campo atgOrderId no puede estar vacío")
    private String atgOrderId;

    @Schema(description = "Identificador del shipping group ATG", example = "sg12345678")
    @NotBlank(message = "El campo atgShippingGroupId no puede estar vacío")
    private String atgShippingGroupId;

    @Schema(description = "Origen de la transacción", example = "LIVERPOOL")
    @NotBlank(message = "El campo source no puede estar vacío")
    private String source;
}
