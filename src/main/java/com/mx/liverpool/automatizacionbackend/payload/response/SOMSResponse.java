package com.mx.liverpool.automatizacionbackend.payload.response;

import com.mx.liverpool.automatizacionbackend.model.BC;
import com.mx.liverpool.automatizacionbackend.model.Hrd;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SOMSResponse {
    private List<String> universo;
    private List<BC> atgCobradas;
    private List<BC> decommCobradas;
    private List<String> noCobradas;
    private List<Hrd> hrd;
    private List<String> noHrd;
}
