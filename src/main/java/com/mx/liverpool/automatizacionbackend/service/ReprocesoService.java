package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@Log4j2
public class ReprocesoService {
    private final WebClient webClient;
    private final long pausaEnvioMs;
    private final long pausaLoteMs;
    private final int tamanoLote;

    @Autowired
    public ReprocesoService(WebClient.Builder webClientBuilder,
                            @Value("${reproceso.i200.base-url}") String baseUrl,
                            @Value("${reproceso.i200.apikey}") String apikey,
                            @Value("${reproceso.pausa-envio-ms}") long pausaEnvioMs,
                            @Value("${reproceso.pausa-lote-ms}") long pausaLoteMs,
                            @Value("${reproceso.tamano-lote}") int tamanoLote) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("apikey", apikey)
                .build();
        this.pausaEnvioMs = pausaEnvioMs;
        this.pausaLoteMs = pausaLoteMs;
        this.tamanoLote = tamanoLote;
    }

    public <R> List<ReprocesoResult> reprocesar(List<R> filas,
                                                Function<R, String> tracking,
                                                Function<R, Preparado> preparar) {
        log.info("Entrando a reprocesar con {} filas", filas.size());
        List<ReprocesoResult> resultados = new ArrayList<>();
        int enviados = 0;
        for (int i = 0; i < filas.size(); i++) {
            R fila = filas.get(i);
            String trackingNumber = tracking.apply(fila);
            Preparado preparado = preparar.apply(fila);
            if (!preparado.debeEnviar()) {
                resultados.add(construirResultado(preparado.requestOriginal(), trackingNumber, preparado.responseSiNoEnvia()));
                continue;
            }
            resultados.add(enviar(preparado.requestOriginal(), trackingNumber));
            enviados++;
            if (i < filas.size() - 1) {
                pausar(enviados % tamanoLote == 0 ? pausaLoteMs : pausaEnvioMs);
            }
        }
        log.info("Finalizando reprocesar con {} resultados ({} enviados)", resultados.size(), enviados);
        return resultados;
    }

    private ReprocesoResult enviar(String requestOriginal, String trackingNumber) {
        log.info("Enviando reproceso para tracking {}", trackingNumber);
        try {
            String response = webClient.post()
                    .uri("/oms/sl/I200?origen=ecom")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestOriginal)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return construirResultado(requestOriginal, trackingNumber, response == null ? "" : response);
        } catch (WebClientResponseException e) {
            log.error("Error HTTP enviando tracking {}: {}", trackingNumber, e.getMessage());
            return construirResultado(requestOriginal, trackingNumber,
                    "\"error\": \"" + e.getMessage() + "\", \"body\": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error enviando tracking {}: {}", trackingNumber, e.getMessage());
            return construirResultado(requestOriginal, trackingNumber, "\"error\": \"" + e.getMessage() + "\"");
        }
    }

    private ReprocesoResult construirResultado(String requestOriginal, String trackingNumber, String response) {
        return ReprocesoResult.builder()
                .requestOriginal(requestOriginal)
                .trackingNumber(trackingNumber)
                .response(response == null ? "" : response)
                .build();
    }

    private void pausar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Pausa entre envíos interrumpida: {}", e.getMessage());
        }
    }

    // Resultado de transformar una fila: si debe enviarse y el body (o el motivo cuando se omite).
    public record Preparado(boolean debeEnviar, String requestOriginal, String responseSiNoEnvia) {
        public static Preparado enviar(String body) {
            return new Preparado(true, body, null);
        }

        public static Preparado omitir(String requestOriginal, String motivo) {
            return new Preparado(false, requestOriginal, motivo);
        }
    }
}
