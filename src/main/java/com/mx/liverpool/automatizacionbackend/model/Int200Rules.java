package com.mx.liverpool.automatizacionbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Reglas del INT200 cargadas desde {@code int200-rules.json}. Un cambio de catálogo u
 * obligatoriedad se hace en ese recurso, sin recompilar.
 * <p>
 * En {@code path}, el segmento terminado en {@code []} indica que ese nodo es un arreglo y que el
 * bloque se evalúa una vez por elemento. El {@code path} vacío es la raíz {@code /Order}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Int200Rules(
        List<Bloque> bloques,
        Map<String, List<String>> catalogos,
        List<String> vaciosEsperados,
        Map<String, List<String>> discrepancias) {

    public Int200Rules {
        bloques = bloques == null ? List.of() : bloques;
        catalogos = catalogos == null ? Map.of() : catalogos;
        vaciosEsperados = vaciosEsperados == null ? List.of() : vaciosEsperados;
        discrepancias = discrepancias == null ? Map.of() : discrepancias;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bloque(String path, List<Campo> campos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Campo(String nombre, Integer longitud, String valorDefault, String catalogo) {}
}
