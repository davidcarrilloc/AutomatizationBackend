package com.mx.liverpool.automatizacionbackend.scheduler;

import com.mx.liverpool.automatizacionbackend.service.ExtractoBcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class ExtractoBcScheduler {

    private final ExtractoBcService extractoBcService;

    @Scheduled(cron = "0 0 12 * * *")
    public void executeTask() {
        log.info("Ejecutando ExtractoBcScheduler");
        extractoBcService.generarDiaAnterior();
        log.info("Finalizando ExtractoBcScheduler");
    }
}
