package com.mx.liverpool.automatizacionbackend.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FulfillmentRequest {
    @JsonProperty("processes")
    private List<Process> processes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Process {
        @JsonProperty("trackingNumber")
        private String trackingNumber;
    }
}
