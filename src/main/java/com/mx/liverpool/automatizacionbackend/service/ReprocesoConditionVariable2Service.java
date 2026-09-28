package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mx.liverpool.automatizacionbackend.model.ClienteRemision;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import com.mx.liverpool.automatizacionbackend.repository.RemisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReprocesoConditionVariable2Service {
    private static final String SIN_DATOS = "Sin datos en BRIDGECORE";
    private static final String CAMPO = "ConditionVariable2";

    private final ReprocesoService reprocesoService;
    private final RemisionRepository remisionRepository;
    private final ObjectMapper objectMapper;

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar conditionVariable2 con {} filas", filas.size());

        Map<String, ClienteRemision> porRemision = new HashMap<>();
        List<String> remisiones = filas.stream().map(this::remision).filter(r -> !r.isEmpty()).distinct().toList();
        for (String remision : remisiones) {
            List<ClienteRemision> clientes = remisionRepository.obtenerClienteRemision(remision);
            log.info("BRIDGECORE devolvió {} filas para la remisión {}", clientes.size(), remision);
            if (!clientes.isEmpty()) porRemision.put(remision, clientes.getFirst());
        }

        List<ReprocesoResult> resultados = reprocesoService.reprocesar(
                filas, ReprocesoNodeRow::getTrackingNumber, fila -> preparar(fila, porRemision));

        log.info("Finalizando reprocesar conditionVariable2");
        return resultados;
    }

    private ReprocesoService.Preparado preparar(ReprocesoNodeRow fila, Map<String, ClienteRemision> porRemision) {
        try {
            ClienteRemision cliente = porRemision.get(remision(fila));

            if (cliente == null) {
                log.info("La remisión {} no tiene transacción en BRIDGECORE, se omite el envío", fila.getTrackingNumber());
                return ReprocesoService.Preparado.omitir(fila.getJson(), SIN_DATOS);
            }

            JsonNode raiz = objectMapper.readTree(fila.getJson());
            JsonNode orden = raiz.has("Order") ? raiz.get("Order") : raiz;

            if (!rellenar(orden, valor(cliente.getIsClickAndCollct()))) {
                log.info("La remisión {} tiene {} vacío y BRIDGECORE no trae is_click_and_collct válido, se omite el envío",
                        fila.getTrackingNumber(), CAMPO);
                return ReprocesoService.Preparado.omitir(fila.getJson(), SIN_DATOS + " para: " + CAMPO);
            }
            return ReprocesoService.Preparado.enviar(objectMapper.writeValueAsString(raiz));
        } catch (Exception e) {
            log.error("Error construyendo el body para la remisión {}: {}", fila.getTrackingNumber(), e.getMessage());
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }
    }

    /** Y = click & collect (PICK), N = envío a domicilio (SHP); cualquier otro valor no alcanza para decidir. */
    static String valor(String isClickAndCollct) {
        String flag = Objects.toString(isClickAndCollct, "").trim();
        if (flag.equalsIgnoreCase("Y")) return "PICK";
        if (flag.equalsIgnoreCase("N")) return "SHP";
        return null;
    }

    /**
     * Llena ConditionVariable2 en cada OrderLine donde venga vacío o no exista (la llave se crea); un valor
     * propio del pedido se respeta. Devuelve false si alguna línea quedó vacía porque no hay valor que poner.
     */
    static boolean rellenar(JsonNode orden, String valor) {
        boolean completo = true;
        for (JsonNode linea : orden.path("OrderLines")) {
            if (linea.path("OrderLine") instanceof ObjectNode orderLine
                    && ReprocesoBillToService.estaVacio(orderLine.get(CAMPO))) {
                if (valor == null) completo = false;
                else orderLine.put(CAMPO, valor);
            }
        }
        return completo;
    }

    private String remision(ReprocesoNodeRow fila) {
        return Objects.toString(fila.getTrackingNumber(), "").trim();
    }
}
