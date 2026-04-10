package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.NoOMS;
import com.mx.liverpool.automatizacionbackend.repository.OMSRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
public class OMSService {
    private final OMSRepository omsRepository;

    @Autowired
    public OMSService(OMSRepository omsRepository) {
        this.omsRepository = omsRepository;
    }

    public void obtenerReporteNoOMS() {
        LocalDateTime fechaFin = LocalDateTime.now();
        LocalDateTime fechaInicio = fechaFin.minusDays(1);

        fechaInicio = fechaInicio.withHour(0).withMinute(0).withSecond(0).withNano(0);
        fechaFin = fechaFin.withHour(0).withMinute(0).withSecond(0).withNano(0);

        log.info("Fecha inicio: {}, fecha fin: {}", fechaInicio, fechaFin);
        List<NoOMS> result = omsRepository.obtenerOrdenesNoOMS(fechaInicio, fechaFin);
        for (NoOMS noOMS : result) {
            log.info("NoOMS: {}", noOMS);
            if (noOMS.getTotal() > 90) {
                log.warn("Alerta: NoOMS con total mayor a 90: {}", noOMS);
            }
        }
    }
}
