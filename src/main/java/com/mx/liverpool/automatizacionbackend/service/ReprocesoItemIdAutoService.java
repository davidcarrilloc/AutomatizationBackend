package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mx.liverpool.automatizacionbackend.model.DetalleSkuRemision;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoNodeRow;
import com.mx.liverpool.automatizacionbackend.model.ReprocesoResult;
import com.mx.liverpool.automatizacionbackend.repository.RemisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReprocesoItemIdAutoService {
    private static final String SIN_DATOS = "Sin datos en BRIDGECORE";
    private static final String SIN_CORREO = "Sin correo en BRIDGECORE";
    private static final String CAMPO_CORREO = "EMailID";

    private final ReprocesoService reprocesoService;
    private final RemisionRepository remisionRepository;
    private final ObjectMapper objectMapper;

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar itemid automático con {} filas", filas.size());

        // La remisión se consulta tal como viene en el Excel y se guarda con esa misma llave: agrupar
        // por la columna que devuelve BRIDGECORE dejaba fuera toda remisión escrita de otra forma.
        Map<String, List<DetalleSkuRemision>> porRemision = new HashMap<>();
        List<String> remisiones = filas.stream().map(this::remision).filter(r -> !r.isEmpty()).distinct().toList();
        for (String remision : remisiones) {
            List<DetalleSkuRemision> detalle = remisionRepository.obtenerDetalleSkuRemision(remision);
            log.info("BRIDGECORE devolvió {} filas para la remisión {}", detalle.size(), remision);
            porRemision.put(remision, detalle);
        }

        Map<ReprocesoNodeRow, List<String>> extras = new IdentityHashMap<>();
        Map<ReprocesoNodeRow, String> correos = new IdentityHashMap<>();
        List<ReprocesoResult> resultados = reprocesoService.reprocesar(
                filas, ReprocesoNodeRow::getTrackingNumber, fila -> preparar(fila, porRemision, extras, correos));

        for (int i = 0; i < resultados.size(); i++) {
            resultados.get(i).setSkus(extras.get(filas.get(i)));
            resultados.get(i).setCorreo(correos.get(filas.get(i)));
        }

        log.info("Finalizando reprocesar itemid automático");
        return resultados;
    }

    private ReprocesoService.Preparado preparar(ReprocesoNodeRow fila,
                                                Map<String, List<DetalleSkuRemision>> porRemision,
                                                Map<ReprocesoNodeRow, List<String>> extras,
                                                Map<ReprocesoNodeRow, String> correos) {
        try {
            List<DetalleSkuRemision> skus = porRemision.getOrDefault(remision(fila), List.of());

            if (skus.isEmpty()) {
                log.info("La remisión {} no tiene detalle en BRIDGECORE, se omite el envío", fila.getTrackingNumber());
                return ReprocesoService.Preparado.omitir(fila.getJson(), SIN_DATOS);
            }

            JsonNode raiz = objectMapper.readTree(fila.getJson());
            List<JsonNode> orderLines = new ArrayList<>();
            raiz.path("OrderLines").forEach(orderLines::add);

            // Los SKUs se asignan por posición, así como vienen de BRIDGECORE. Si las cuentas no
            // cuadran no hay asignación confiable: se reporta y se colocan a mano.
            if (orderLines.size() != skus.size()) {
                log.info("La remisión {} trae {} SKUs y el JSON {} OrderLines, se deja para colocar manualmente",
                        fila.getTrackingNumber(), skus.size(), orderLines.size());
                extras.put(fila, skus.stream().map(this::describir).toList());
                correos.put(fila, Objects.toString(skus.getFirst().getCustomerEmail(), ""));
                return ReprocesoService.Preparado.omitir(fila.getJson(),
                        "No enviado: BC trae " + skus.size() + " SKUs y el JSON " + orderLines.size() + " OrderLines");
            }

            llenarItem(orderLines, skus, false);
            if (!rellenarCampos(raiz, Collections.singletonMap(CAMPO_CORREO, skus.getFirst().getCustomerEmail())).isEmpty()) {
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

    /**
     * Asigna los SKUs de BRIDGECORE a los OrderLine por posición. Con soloVacios el dato propio del
     * pedido se respeta y solo se llenan los huecos; un precio en cero cuenta como hueco, que es como
     * ATG manda las órdenes a las que les falta el importe.
     */
    static void llenarItem(List<JsonNode> orderLines, List<DetalleSkuRemision> skus, boolean soloVacios) {
        for (int i = 0; i < orderLines.size(); i++) {
            DetalleSkuRemision sku = skus.get(i);
            JsonNode orden = orderLines.get(i).path("OrderLine");
            if (orden.path("Item") instanceof ObjectNode itemNode) {
                if (!soloVacios || ReprocesoBillToService.estaVacio(itemNode.get("ItemDesc"))) {
                    itemNode.put("ItemDesc", sku.getDisplayName());
                }
                // El ItemID viaja siempre como texto, aunque el SKU sean puros dígitos.
                if (!soloVacios || ReprocesoBillToService.estaVacio(itemNode.get("ItemID"))) {
                    itemNode.put("ItemID", Objects.toString(sku.getSkuId(), "").trim());
                }
            }
            if (orden.path("LinePriceInfo") instanceof ObjectNode precioNode) {
                String precio = formatearPrecio(sku.getTotalSku());
                if (!soloVacios || sinPrecio(precioNode.get("UnitPrice"))) precioNode.put("UnitPrice", precio);
                if (!soloVacios || sinPrecio(precioNode.get("ListPrice"))) precioNode.put("ListPrice", precio);
            }
        }
    }

    /** Un precio ausente, vacío o en cero es un hueco: el pedido llegó sin importe. */
    static boolean sinPrecio(JsonNode valor) {
        if (ReprocesoBillToService.estaVacio(valor)) return true;
        try {
            return new BigDecimal(valor.asText().trim()).signum() == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Escribe cada valor en toda llave homónima vacía del JSON, a cualquier profundidad. Solo llena las
     * que ya existen: un nodo sin la llave no la gana, y un valor propio del pedido se respeta. Una
     * llave ausente del mapa ni se llena ni se reclama, que es como el llamador dice qué es opcional.
     * Devuelve los campos que quedaron vacíos, que es lo que impide enviar la orden.
     */
    static List<String> rellenarCampos(JsonNode nodo, Map<String, String> valores) {
        List<String> faltantes = new ArrayList<>();
        if (nodo instanceof ObjectNode objeto) {
            for (Map.Entry<String, String> campo : valores.entrySet()) {
                if (objeto.path(campo.getKey()).isMissingNode()
                        || !ReprocesoBillToService.estaVacio(objeto.get(campo.getKey()))) continue;
                if (campo.getValue() == null || campo.getValue().isBlank()) faltantes.add(campo.getKey());
                else objeto.put(campo.getKey(), campo.getValue().trim());
            }
        }
        for (JsonNode hijo : nodo) {
            faltantes.addAll(rellenarCampos(hijo, valores));
        }
        return faltantes;
    }

    private String describir(DetalleSkuRemision sku) {
        return Objects.toString(sku.getSkuId(), "") + " | "
                + Objects.toString(sku.getDisplayName(), "") + " | "
                + formatearPrecio(sku.getTotalSku());
    }

    static String formatearPrecio(BigDecimal total) {
        return (total == null ? BigDecimal.ZERO : total).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String remision(ReprocesoNodeRow fila) {
        return Objects.toString(fila.getTrackingNumber(), "").trim();
    }
}
