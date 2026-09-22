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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Log4j2
public class ReprocesoFirstNameService {
    private static final String SIN_DATOS = "Sin datos en BRIDGECORE";
    private static final String CAMPO_CORREO = "EMailID";
    private static final String CAMPO_NOMBRE = "FirstName";
    private static final String CAMPO_MEDIO = "MiddleName";
    private static final String CAMPO_APELLIDO = "LastName";

    private final ReprocesoService reprocesoService;
    private final RemisionRepository remisionRepository;
    private final ObjectMapper objectMapper;

    public List<ReprocesoResult> reprocesar(List<ReprocesoNodeRow> filas) {
        log.info("Entrando a reprocesar firstName con {} filas", filas.size());

        // La remisión se consulta y se guarda con la misma llave, tal como viene en el Excel: si la de
        // armado y la de búsqueda difieren, toda remisión reportaría "Sin datos" aunque BC sí responda.
        Map<String, ClienteRemision> porRemision = new HashMap<>();
        List<String> remisiones = filas.stream().map(this::remision).filter(r -> !r.isEmpty()).distinct().toList();
        for (String remision : remisiones) {
            List<ClienteRemision> clientes = remisionRepository.obtenerClienteRemision(remision);
            log.info("BRIDGECORE devolvió {} filas para la remisión {}", clientes.size(), remision);
            if (!clientes.isEmpty()) porRemision.put(remision, clientes.getFirst());
        }

        List<ReprocesoResult> resultados = reprocesoService.reprocesar(
                filas, ReprocesoNodeRow::getTrackingNumber, fila -> preparar(fila, porRemision));

        log.info("Finalizando reprocesar firstName");
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

            // Solo se llenan las llaves que ya existen y vienen vacías: un dato propio del pedido se
            // respeta y un nodo sin la llave no la gana. El veto mira el resultado, no la fuente: un
            // pedido que ya trae sus datos se envía aunque BRIDGECORE no devuelva cliente.
            List<String> faltantes = ReprocesoItemIdAutoService.rellenarCampos(raiz, valores(cliente))
                    .stream().distinct().sorted().toList();
            if (!faltantes.isEmpty()) {
                log.info("La remisión {} quedó con {} vacíos y BRIDGECORE no los trae, se omite el envío",
                        fila.getTrackingNumber(), faltantes);
                return ReprocesoService.Preparado.omitir(fila.getJson(),
                        SIN_DATOS + " para: " + String.join(", ", faltantes));
            }
            return ReprocesoService.Preparado.enviar(objectMapper.writeValueAsString(raiz));
        } catch (Exception e) {
            log.error("Error construyendo el body para la remisión {}: {}", fila.getTrackingNumber(), e.getMessage());
            return ReprocesoService.Preparado.omitir(fila.getJson(), "\"error\": \"JSON inválido: " + e.getMessage() + "\"");
        }
    }

    /**
     * BRIDGECORE guarda el nombre completo en una sola columna, así que se reparte por posición:
     * primera palabra al FirstName, última al LastName y todo lo de en medio junto al MiddleName, de
     * modo que ninguna parte del nombre se pierde. El MiddleName solo entra al mapa cuando hay algo
     * que ponerle: no es obligatorio en el INT200 y exigirlo dejaría fuera a todo nombre de dos
     * palabras.
     */
    static Map<String, String> valores(ClienteRemision cliente) {
        String[] partes = Objects.toString(cliente.getNombreUsuario(), "").trim().split("\\s+");
        Map<String, String> valores = new HashMap<>();
        valores.put(CAMPO_CORREO, cliente.getCustomerEmail());
        valores.put(CAMPO_NOMBRE, partes[0]);
        valores.put(CAMPO_APELLIDO, partes.length > 1 ? partes[partes.length - 1] : "");
        if (partes.length > 2) {
            valores.put(CAMPO_MEDIO, String.join(" ", Arrays.copyOfRange(partes, 1, partes.length - 1)));
        }
        return valores;
    }

    private String remision(ReprocesoNodeRow fila) {
        return Objects.toString(fila.getTrackingNumber(), "").trim();
    }
}
