package com.mx.liverpool.automatizacionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ComparativaTx {
    private String nombreHoja;
    private String titulo;
    private List<TxDiffPorHora> ayer;
    private List<TxDiffPorHora> hoy;
}
