package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.payload.response.ResponseTxDiferencia;
import org.springframework.stereotype.Service;

@Service
public class TxService {
    public Object obtenerDiferenciaTxHoyvsAyer() {


        return ResponseTxDiferencia.builder()
                .txsHoy()
                .txsAyer()
                .build();
    }
}
