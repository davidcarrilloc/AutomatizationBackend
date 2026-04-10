package com.mx.liverpool.automatizacionbackend.scheduler;

import com.mx.liverpool.automatizacionbackend.service.OMSService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class NoOMSScheduler {
    private final OMSService omsService;

    @Autowired
    public NoOMSScheduler(OMSService omsService) {
        this.omsService = omsService;
    }

    @Scheduled(cron = "0 0 8,16 * * *")
    public void executeTask() {
        log.info("Ejecutando NoOMSScheduler");
        omsService.obtenerReporteNoOMS();
        log.info("Finalizando NoOMSScheduler");
    }
}
