package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CodigoDigitalResponse {
    private List<JsonResponse> response;
    private List<String> noEncontrados;

    @Builder
    public static class JsonResponse {
        private String refTransId;
        private String codigo;
    }
}
