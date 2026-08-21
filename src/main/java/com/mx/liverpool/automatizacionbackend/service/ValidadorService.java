package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.liverpool.automatizacionbackend.model.Int200Rules;
import com.mx.liverpool.automatizacionbackend.model.MarketplaceResult;
import com.mx.liverpool.automatizacionbackend.model.MarketplaceRow;
import com.mx.liverpool.automatizacionbackend.model.ValidacionResult;
import com.mx.liverpool.automatizacionbackend.model.ValidacionRow;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ValidadorService {

    private static final Pattern MOJIBAKE = Pattern.compile("[ÃÂ]|â€");

    private final ObjectMapper objectMapper;
    private final Int200Rules reglas;

    @Autowired
    public ValidadorService(ObjectMapper objectMapper,
                            @Value("${int200.rules:classpath:int200-rules.json}") Resource recursoReglas) {
        this.objectMapper = objectMapper;
        try (InputStream is = recursoReglas.getInputStream()) {
            this.reglas = objectMapper.readValue(is, Int200Rules.class);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron cargar las reglas del INT200: " + e.getMessage());
        }
    }

    public List<ValidacionResult> validar(List<ValidacionRow> filas) {
        log.info("Entrando a validar con {} filas", filas.size());
        List<ValidacionResult> resultados = filas.stream().map(this::validarFila).toList();
        log.info("Finalizando validar");
        return resultados;
    }

    public List<MarketplaceResult> extraerMarketplace(List<MarketplaceRow> filas) {
        log.info("Entrando a extraerMarketplace con {} filas", filas.size());
        List<MarketplaceResult> resultados = filas.stream().flatMap(fila -> extraerFila(fila).stream()).toList();
        log.info("Finalizando extraerMarketplace con {} registros", resultados.size());
        return resultados;
    }

    /** Una fila puede producir varios registros: uno por cada offer del REQUEST. */
    private List<MarketplaceResult> extraerFila(MarketplaceRow fila) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(fila.getRequest());
        } catch (JsonProcessingException e) {
            log.warn("REQUEST no parseable del tracking {}: {}", fila.getTrackingNumber(), e.getOriginalMessage());
            return List.of(MarketplaceResult.builder().errores("JSON inválido: " + e.getOriginalMessage()).build());
        }

        String remision = texto(raiz, "commercial_id");
        String faltaRemision = remision.isEmpty() ? "Falta commercial_id" : null;

        JsonNode offers = raiz.path("offers");
        if (!offers.isArray() || offers.isEmpty())
            return List.of(MarketplaceResult.builder()
                    .remision(remision)
                    .errores(unir(faltaRemision, "El REQUEST no trae offers"))
                    .build());

        List<MarketplaceResult> registros = new ArrayList<>();
        for (JsonNode offer : offers) {
            String offerId = texto(offer, "offer_id");
            String sku = "";
            for (JsonNode campo : offer.path("order_line_additional_fields")) {
                if (!"product-sap-sku-id".equals(texto(campo, "code"))) continue;
                sku = texto(campo, "value");
                break;
            }
            registros.add(MarketplaceResult.builder()
                    .remision(remision)
                    .offerId(offerId)
                    .sku(sku)
                    .errores(unir(faltaRemision,
                            offerId.isEmpty() ? "Falta offer_id" : null,
                            sku.isEmpty() ? "Falta product-sap-sku-id" : null))
                    .build());
        }
        return registros;
    }

    private String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? "" : valor.asText().trim();
    }

    private String unir(String... mensajes) {
        return Arrays.stream(mensajes).filter(Objects::nonNull).collect(Collectors.joining(", "));
    }

    private ValidacionResult validarFila(ValidacionRow fila) {
        List<String> faltantes = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        try {
            JsonNode raiz = objectMapper.readTree(fila.getJson());
            // ATG envía el pedido sin llave envolvente y la fachada sí la manda: se toleran ambas entradas.
            JsonNode orden = raiz.has("Order") ? raiz.get("Order") : raiz;

            reglas.bloques().forEach(bloque -> revisarBloque(orden, bloque, faltantes, errores));

            reglas.vaciosEsperados().forEach(ruta -> porCampo(orden, ruta, (r, v) ->
                    errores.add("CTR-010 " + r + ": la definición pide enviarlo vacío y llega con \"" + v + "\"")));

            reglas.discrepancias().forEach((ruta, permitidos) -> porCampo(orden, ruta, (r, v) -> {
                if (!permitidos.contains(v))
                    errores.add("CTR-008 " + r + ": \"" + v + "\" no está en el catálogo INT200 (discrepancia conocida, no se rechaza)");
            }));

            revisarMojibake(orden, "", errores);
        } catch (JsonProcessingException e) {
            errores.add("JSON inválido: " + e.getOriginalMessage());
        }

        return ValidacionResult.builder()
                .json(fila.getJson())
                .remision(fila.getRemision())
                .faltantes(String.join(", ", faltantes))
                .errores(String.join(", ", errores))
                .build();
    }

    private void revisarBloque(JsonNode orden, Int200Rules.Bloque bloque, List<String> faltantes, List<String> errores) {
        List<String> ausentes = new ArrayList<>();
        for (Ubicacion ubicacion : resolver(orden, bloque.path(), ausentes)) {
            for (Int200Rules.Campo campo : bloque.campos()) revisarCampo(ubicacion, campo, faltantes, errores);
        }
        faltantes.addAll(ausentes);
    }

    private void revisarCampo(Ubicacion ubicacion, Int200Rules.Campo campo, List<String> faltantes, List<String> errores) {
        String ruta = ubicacion.ruta() + campo.nombre();
        JsonNode valor = ubicacion.nodo().get(campo.nombre());

        if (valor == null) {
            faltantes.add(ruta);
            return;
        }

        String texto = valor.isNull() ? "" : valor.asText().trim();
        if (texto.isEmpty()) {
            // Un obligatorio vacío con default en la definición no es un hueco: se rellena al armar el mensaje.
            if (campo.valorDefault() == null) faltantes.add(ruta);
            else errores.add("CTR-002 " + ruta + ": vacío, aplica default \"" + campo.valorDefault() + "\"");
            return;
        }

        List<String> permitidos = campo.catalogo() == null ? null : reglas.catalogos().get(campo.catalogo());
        if (permitidos != null && !permitidos.contains(texto))
            errores.add("CTR-004 " + ruta + ": \"" + texto + "\" fuera de catálogo " + permitidos);

        if (campo.longitud() != null && texto.length() > campo.longitud())
            errores.add("CTR-005 " + ruta + ": longitud " + texto.length() + " excede " + campo.longitud());
    }

    /**
     * Recorre {@code path} desde {@code base} y devuelve un nodo por cada coincidencia. El segmento
     * terminado en {@code []} se expande a todos los elementos del arreglo. Los tramos que no existen
     * se acumulan en {@code ausentes} en lugar de detener el recorrido.
     */
    private List<Ubicacion> resolver(JsonNode base, String path, List<String> ausentes) {
        List<Ubicacion> actuales = new ArrayList<>(List.of(new Ubicacion("", base)));
        if (path == null || path.isEmpty()) return actuales;

        for (String segmento : path.split("\\.")) {
            boolean arreglo = segmento.endsWith("[]");
            String nombre = arreglo ? segmento.substring(0, segmento.length() - 2) : segmento;
            List<Ubicacion> siguientes = new ArrayList<>();

            for (Ubicacion actual : actuales) {
                JsonNode hijo = actual.nodo().get(nombre);
                if (hijo == null || hijo.isNull() || (arreglo && !hijo.isArray())) {
                    ausentes.add(actual.ruta() + nombre);
                    continue;
                }
                if (arreglo) {
                    for (int i = 0; i < hijo.size(); i++)
                        siguientes.add(new Ubicacion(actual.ruta() + nombre + "[" + i + "].", hijo.get(i)));
                } else {
                    siguientes.add(new Ubicacion(actual.ruta() + nombre + ".", hijo));
                }
            }
            actuales = siguientes;
        }
        return actuales;
    }

    /** Aplica {@code accion(ruta, valor)} sobre cada ocurrencia poblada de una ruta campo a campo. */
    private void porCampo(JsonNode orden, String rutaCompleta, BiConsumer<String, String> accion) {
        int corte = rutaCompleta.lastIndexOf('.');
        String pathBloque = corte < 0 ? "" : rutaCompleta.substring(0, corte);
        String nombre = corte < 0 ? rutaCompleta : rutaCompleta.substring(corte + 1);

        for (Ubicacion ubicacion : resolver(orden, pathBloque, new ArrayList<>())) {
            JsonNode valor = ubicacion.nodo().get(nombre);
            if (valor == null || valor.isNull()) continue;
            String texto = valor.asText().trim();
            if (!texto.isEmpty()) accion.accept(ubicacion.ruta() + nombre, texto);
        }
    }

    /** El mojibake es transversal (contrato §2), no se limita a los campos obligatorios. */
    private void revisarMojibake(JsonNode nodo, String ruta, List<String> errores) {
        if (nodo.isObject()) {
            nodo.properties().forEach(campo ->
                    revisarMojibake(campo.getValue(), ruta.isEmpty() ? campo.getKey() : ruta + "." + campo.getKey(), errores));
        } else if (nodo.isArray()) {
            for (int i = 0; i < nodo.size(); i++) revisarMojibake(nodo.get(i), ruta + "[" + i + "]", errores);
        } else if (nodo.isTextual() && MOJIBAKE.matcher(nodo.asText()).find()) {
            errores.add("CTR-006 " + ruta + ": mojibake detectado en \"" + nodo.asText() + "\"");
        }
    }

    private record Ubicacion(String ruta, JsonNode nodo) {}
}
