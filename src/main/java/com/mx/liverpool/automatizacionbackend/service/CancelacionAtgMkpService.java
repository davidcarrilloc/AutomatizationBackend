package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.AtgMarketplace;
import com.mx.liverpool.automatizacionbackend.model.Dummy;
import com.mx.liverpool.automatizacionbackend.repository.AtgMirklRepository;
import com.mx.liverpool.automatizacionbackend.repository.RemisionRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class CancelacionAtgMkpService {
    private final RemisionRepository remisionRepository;
    private final AtgMirklRepository atgMirklRepository;
    private final ExcelService excelService;

    @Autowired
    public CancelacionAtgMkpService(RemisionRepository remisionRepository, AtgMirklRepository atgMirklRepository,
                                    ExcelService excelService) {
        this.remisionRepository = remisionRepository;
        this.atgMirklRepository = atgMirklRepository;
        this.excelService = excelService;
    }

    public String executeCancelacionesProcess(List<Map<Integer, String>> objects) {
        List<String> remisiones = new ArrayList<>();
        List<String> offerIds = new ArrayList<>();
        List<Dummy> dummies = new ArrayList<>();
        for (Map<Integer, String> row : objects) {
            String remision = String.format("%10s", row.get(0)).replace(' ', '0');
            String offerId = row.get(1);
            String leadTime = row.get(2);
            String sku = row.get(7);

            log.info("Procesando la remision: {}, offerId: {}, leadTime: {}, sku: {}",
                    remision, offerId, leadTime, sku);

            remisiones.add(remision);
            offerIds.add(offerId);
            dummies.add(Dummy.builder()
                            .remision(remision)
                            .leadTime(leadTime)
                            .sku(sku)
                            .offerId(offerId)
                            .build());
        }

        validarRemisionesPago(remisiones);
        validarOfferIdExistencia(offerIds);

        List<AtgMarketplace> atgMarketplaces = obtenerDatosBridgecoreDevolucionApv(remisiones);
        String excelId = excelService.crearReporteCancelacion(dummies, atgMarketplaces);

        // Execute python to json
        // Sending python

        return excelId;
    }

    // TODO: Fix validaciones, puede que oracle arroje mas o menos o duplicados, debemos revisar uno por uno
    private void validarRemisionesPago(List<String> remisiones) {
        var remisionesCobradas = remisionRepository.obtenerCobroRemisiones(remisiones);
        if (remisionesCobradas.isEmpty()) throw new IllegalStateException("Ninguna de las remisiones proporcionadas ha sido cobrada.");
        if (remisionesCobradas.size() < remisiones.size()) {
            List<String> remisionesNoCobradas = new ArrayList<>(remisiones);
            remisionesCobradas.forEach(remision -> remisionesNoCobradas.remove(remision.getRemision()));
            throw new IllegalStateException("Las siguientes remisiones no han sido cobradas: " + String.join(", ", remisionesNoCobradas));
        }
    }

    private void validarOfferIdExistencia(List<String> offerIds) {
        var remisionesCobradas = atgMirklRepository.obtenerExistenciaOfferIds(offerIds);
        if (remisionesCobradas.isEmpty()) throw new IllegalStateException("Ninguno de los offerId existe.");
        if (remisionesCobradas.size() < offerIds.size()) {
            List<String> offerIdsNoEncontrados = new ArrayList<>(offerIds);
            remisionesCobradas.forEach(offerid -> offerIdsNoEncontrados.remove(offerid.getOfferId()));
            throw new IllegalStateException("Los siguientes offerId no existen: " + String.join(", ", offerIdsNoEncontrados));
        }
    }

    private List<AtgMarketplace> obtenerDatosBridgecoreDevolucionApv(List<String> remisiones) {
        var datosBridgecore = atgMirklRepository.obtenerDatosBridgecoreDevolucionApv(remisiones);
        if (datosBridgecore.isEmpty()) throw new IllegalStateException("No se encontraron datos de Bridgecore para las remisiones proporcionadas.");
        if (datosBridgecore.size() < remisiones.size()) {
            List<String> remisionesNoEncontradas = new ArrayList<>(remisiones);
            datosBridgecore.forEach(dato -> remisionesNoEncontradas.remove(dato.getRemision()));
            throw new IllegalStateException("No se encontraron datos de Bridgecore para las siguientes remisiones: " + String.join(", ", remisionesNoEncontradas));
        }

        return datosBridgecore;
    }
}
