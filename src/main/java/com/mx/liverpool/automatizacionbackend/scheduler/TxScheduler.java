package com.mx.liverpool.automatizacionbackend.scheduler;

import com.mx.liverpool.automatizacionbackend.service.NotifyService;
import com.mx.liverpool.automatizacionbackend.service.SQLiteService;
import com.mx.liverpool.automatizacionbackend.service.TxService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Log4j2
public class TxScheduler {
    private final TxService txService;
    private final NotifyService notifyService;
    private final SQLiteService sqLiteService;
    private final SimpMessagingTemplate messagingTemplate;
    private static boolean isNotify;

    @Autowired
    public TxScheduler(TxService txService, NotifyService notifyService, SQLiteService sqLiteService, SimpMessagingTemplate messagingTemplate) {
        this.txService = txService;
        this.notifyService = notifyService;
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

    @Scheduled(cron = "*/30 * * * * *")
    public void executeNotify() {
        log.info("Ejecutando Notify");
        if (isNotify) {
            LocalDateTime current = LocalDateTime.now();
            LocalDateTime last2HoursAgo = LocalDateTime.now().minusHours(2);
            var result = txService.obtenerTransaccionesCache(last2HoursAgo, current);
            if (result.size() > 1) notifyService.notificarTx(result);
            messagingTemplate.convertAndSend("/topic/tx", result);
            isNotify = false;
        }
        log.info("Finalizando Notify");
    }
}
