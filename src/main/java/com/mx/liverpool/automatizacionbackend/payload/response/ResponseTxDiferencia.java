package com.mx.liverpool.automatizacionbackend.payload.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public class ResponseTxDiferencia {
    private List<Tx> txsHoy;
    private List<Tx> txsAyer;

    @Builder
    public static class Tx {
        private LocalDateTime fecha;
        private int tx;
    }
}
