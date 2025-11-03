package com.mx.liverpool.automatizacionbackend.payload.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class CopomexResponse {
    private boolean error;
    @JsonProperty("message_error")
    private String mensajeError;
    private CopomexData response;

    @Getter
    public static class CopomexData {
        private String cp;
        @JsonProperty("asentamiento")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> colonias;
        @JsonProperty("tipo_asentamiento")
        private String tipoAsentamiento;
        private String municipio;
        private String estado;
        private String ciudad;
    }
}
