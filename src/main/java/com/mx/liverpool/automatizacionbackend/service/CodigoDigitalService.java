package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.CodigoDigital;
import com.mx.liverpool.automatizacionbackend.repository.CodigoDigitalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CodigoDigitalService {
    private final CodigoDigitalRepository codigosDigitalesRepository;

    @Autowired
    public CodigoDigitalService(CodigoDigitalRepository codigosDigitalesRepository) {
        this.codigosDigitalesRepository = codigosDigitalesRepository;
    }

    public Map<?,?> obtenerCodigosDigitales(List<String> referencias) {
        List<CodigoDigital> codigosDigitales = codigosDigitalesRepository.obtenerCodigosDigitales(referencias);
        List<String> noEncontrados = buscarReferenciasNoEncontradas(referencias, codigosDigitales);
        return Map.of(
                "codigosDigitales", codigosDigitales.stream()
                        .map(cd -> Map.of(
                                "refTransId", cd.getRefTransId(),
                                "codigo", cd.getCodigo()
                        ))
                        .toList(),
                "noEncontrados", noEncontrados
        );
    }

    private List<String> buscarReferenciasNoEncontradas(List<String> referencias, List<CodigoDigital> codigoDigitales) {
        return referencias.stream()
                .filter(ref -> codigoDigitales.stream().noneMatch(cd -> cd.getRefTransId().equals(ref)))
                .toList();
    }
}
