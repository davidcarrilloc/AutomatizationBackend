package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.liverpool.automatizacionbackend.model.ClienteRemision;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import com.mx.liverpool.automatizacionbackend.repository.RemisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReprocesoEmailService {
    private static final String SIN_DATOS = "Sin datos en BRIDGECORE";
    private static final String SIN_CORREO = "Sin correo en BRIDGECORE";
    private static final String CAMPO_CORREO = "EMailID";

    private final ReprocesoService reprocesoService;
    private final RemisionRepository remisionRepository;
    private final ObjectMapper objectMapper;

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar correo con {} filas", filas.size());

        // La remisión se consulta y se guarda con la misma llave, tal como viene en el Excel: si la de
        // armado y la de búsqueda difieren, toda remisión reportaría "Sin datos" aunque BC sí responda.
        Map<String, String> porRemision = new HashMap<>();
        List<String> remisiones = filas.stream().map(this::remision).filter(r -> !r.isEmpty()).distinct().toList();
        for (String remision : remisiones) {
            List<String> correos = remisionRepository.obtenerClienteRemision(remision).stream()
                    .map(ClienteRemision::getCustomerEmail).toList();
            log.info("BRIDGECORE devolvió {} filas para la remisión {}", correos.size(), remision);
            porRemision.put(remision, correos.stream()
                    .filter(c -> c != null && !c.isBlank())
                    .findFirst()
                    .orElse(correos.isEmpty() ? null : ""));
        }

        List<ReprocesoResult> resultados = reprocesoService.reprocesar(
                filas, ReprocesoNodeRow::getTrackingNumber, fila -> preparar(fila, porRemision));

        log.info("Finalizando reprocesar correo");
        return resultados;
    }

    private ReprocesoService.Preparado preparar(ReprocesoNodeRow fila, Map<String, String> porRemision) {
        try {
            String correo = porRemision.get(remision(fila));

            if (correo == null) {
                log.info("La remisión {} no tiene transacción en BRIDGECORE, se omite el envío", fila.getTrackingNumber());
                return ReprocesoService.Preparado.omitir(fila.getJson(), SIN_DATOS);
            }

            JsonNode raiz = objectMapper.readTree(fila.getJson());

            // Solo se llenan las llaves EMailID que ya existen y vienen vacías: un correo propio del
            // pedido se respeta y un nodo sin la llave no la gana.
            if (!ReprocesoItemIdAutoService.rellenarCampos(raiz, Collections.singletonMap(CAMPO_CORREO, correo)).isEmpty()) {
                log.info("La remisión {} no tiene correo en BRIDGECORE y el pedido tampoco lo trae, se omite el envío",
                        fila.getTrackingNumber());
                return ReprocesoService.Preparado.omitir(fila.getJson(), SIN_CORREO);
            }
            return ReprocesoService.Preparado.enviar(objectMapper.writeValueAsString(raiz));
        } catch (Exception e) {
            log.error("Error construyendo el body para la remisión {}: {}", fila.getTrackingNumber(), e.getMessage());
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }
    }

    private String remision(ReprocesoNodeRow fila) {
        return Objects.toString(fila.getTrackingNumber(), "").trim();
    }
}
