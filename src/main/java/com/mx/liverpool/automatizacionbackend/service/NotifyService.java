package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.NoOMS;
import com.mx.liverpool.automatizacionbackend.model.TxPorMinuto;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Log4j2
public class NotifyService {
    private final String EMAIL = "emmtbhkrm7@pomail.net";
    private final String APP_TOKEN = "aqwu4yshx9dptvnxd3td61h9ibsuzk";
    private final String USER_KEY = "ue4q15cpqmcunqouu8bjnct7b9vd7h";
    private final String PUSHOVER_URL = "https://api.pushover.net/1/messages.json";

    private void enviarAlertaCritica(String titulo, String mensaje) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("token", APP_TOKEN);
            params.add("user", USER_KEY);
            params.add("title", titulo);
            params.add("message", mensaje);
            params.add("priority", "2");
            params.add("retry", "30");
            params.add("expire", "3600");
            params.add("sound", "siren");

            restTemplate.postForEntity(PUSHOVER_URL, params, String.class);
            log.info("Alerta de emergencia enviada a Pushover");

        } catch (Exception e) {
            log.error("Falla al enviar notificación Pushover: " + e.getMessage());
        }
    }

    public void notificarTxCanal(List<TxPorMinuto> txs, String canal) {
        List<TxPorMinuto> txsApp = txs.stream()
                .filter(tx -> tx.getSitio() == null)
                .filter(tx -> tx.getCanal().equals(canal))
                .toList();

        long size = txsApp.size() - 1L;
        List<TxPorMinuto> txsAppSinUltimo = txsApp.stream()
                .limit(size)
                .toList();

        double promedioApp = txsAppSinUltimo.stream()
                .mapToInt(TxPorMinuto::getTransacciones)
                .average()
                .orElse(0.0);

        if (promedioApp == 0) {
            log.info("Alerta: No hay transacciones");
            enviarAlertaCritica("Alerta Liverpool", "No hay transacciones en " + canal);
            return;
        }

        var currentTx = txsApp.getLast().getTransacciones();
        var currentTxPercent = (currentTx / promedioApp) * 100;
        if (currentTxPercent < 60) {
            log.info("Alerta: Transacciones por minuto {} es menor al 60% del promedio {}", currentTx, promedioApp);
            enviarAlertaCritica("Alerta Liverpool", "Bajas transacciones en " + canal + ": " + currentTx);
        }
    }

    public void notificarTx(List<TxPorMinuto> txs) {
        notificarTxCanal(txs, "App");
        notificarTxCanal(txs, "Web");
    }

    public void notificarOMS(List<NoOMS> result) {
        for (NoOMS noOMS : result) {
            log.info("NoOMS: {}", noOMS);
            if (noOMS.getTotal() > 90) {
                log.warn("Alerta: NoOMS con total mayor a 90: {}", noOMS);
                enviarAlertaCritica("Alerta Liverpool", "NoOMS con total mayor a 90: " + noOMS.getTotal());
            }
        }
    }
}
