package com.mx.liverpool.automatizacionbackend.scheduler;

import com.mx.liverpool.automatizacionbackend.model.NoOMS;
import com.mx.liverpool.automatizacionbackend.service.NotifyService;
import com.mx.liverpool.automatizacionbackend.service.OMSService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Log4j2
public class NoOMSScheduler {
    private final OMSService omsService;
    private final NotifyService notifyService;

    @Autowired
    public NoOMSScheduler(OMSService omsService, NotifyService notifyService) {
        this.omsService = omsService;
        this.notifyService = notifyService;
    }

    @Scheduled(cron = "0 0 7,10,16,20 * * *")
    public void executeTask() {
        log.info("Ejecutando NoOMSScheduler");
        List<NoOMS> result = omsService.obtenerReporteNoOMS();
        notifyService.notificarOMS(result);
        log.info("Finalizando NoOMSScheduler");
    }
}
