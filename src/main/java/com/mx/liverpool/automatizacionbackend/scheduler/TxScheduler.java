package com.mx.liverpool.automatizacionbackend.scheduler;

import com.mx.liverpool.automatizacionbackend.service.SQLiteService;
import com.mx.liverpool.automatizacionbackend.service.TxService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class TxScheduler {
    private final TxService txService;
    private final SQLiteService sqLiteService;
    private final SimpMessagingTemplate messagingTemplate;
    private static boolean isNotify;

    @Autowired
    public TxScheduler(TxService txService, SQLiteService sqLiteService, SimpMessagingTemplate messagingTemplate) {
        this.txService = txService;
        this.sqLiteService = sqLiteService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(cron = "0 * * * * *")
    public void executeTask() {
        log.info("Ejecutando TxScheduler");
        var result = txService.obtenerTransacciones();
        sqLiteService.insertarInformacion(result);
        isNotify = true;
        log.info("Finalizando TxScheduler");
    }

    @Scheduled(cron = "0 * * * * *")
    public void executeNotify() {
        log.info("Ejecutando Notify");
        if (isNotify) {
            // leer datos y luego enviar
            messagingTemplate.convertAndSend("/topic/tx", "result");
            isNotify = false;
        }
        log.info("Finalizando Notify");
    }
}
