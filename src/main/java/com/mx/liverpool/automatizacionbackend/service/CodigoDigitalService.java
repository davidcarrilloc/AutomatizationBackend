package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.CodigoDigital;
import com.mx.liverpool.automatizacionbackend.payload.response.CodigoDigitalResponse;
import com.mx.liverpool.automatizacionbackend.repository.CodigoDigitalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodigoDigitalService {
    private final CodigoDigitalRepository codigosDigitalesRepository;

    @Autowired
    public CodigoDigitalService(CodigoDigitalRepository codigosDigitalesRepository) {
        this.codigosDigitalesRepository = codigosDigitalesRepository;
    }

    public String obtenerCodigosDigitales(List<String> referencias) {
        List<CodigoDigital> codigosDigitales = codigosDigitalesRepository.obtenerCodigosDigitales(referencias);
        List<String> noEncontrados = buscarReferenciasNoEncontradas(referencias, codigosDigitales);
        return null;
    }

    private List<String> buscarReferenciasNoEncontradas(List<String> referencias, List<CodigoDigital> codigoDigitales) {
        return codigoDigitales.stream()
                .map(CodigoDigital::getRefTransId)
                .filter(refTransId -> !referencias.contains(refTransId))
                .toList();
    }
}
