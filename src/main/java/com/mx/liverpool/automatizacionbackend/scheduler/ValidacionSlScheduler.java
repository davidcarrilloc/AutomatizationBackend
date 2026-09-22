package com.mx.liverpool.automatizacionbackend.scheduler;

import com.mx.liverpool.automatizacionbackend.service.ValidacionSlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Log4j2
public class ValidacionSlScheduler {

    private final ValidacionSlService validacionSlService;

    private LocalDate ultimaCarga;

    @Scheduled(cron = "0 10/10 12-13 * * *")
    public void executeTask() {
        if (LocalDate.now().equals(ultimaCarga)) {
            return;
        }
        log.info("Ejecutando ValidacionSlScheduler");
        try {
            validacionSlService.descargarYGuardar();
            ultimaCarga = LocalDate.now();
        } catch (Exception e) {
            log.error("No se pudieron leer los archivos de validación, se reintenta en 10 minutos: {}", e.getMessage());
        }
        log.info("Finalizando ValidacionSlScheduler");
    }
}
