package com.mx.liverpool.automatizacionbackend.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de VyE")
                        .version("1.0.0")
                        .description("Automatizaciones de Venta y Entrega"));
    }

    private static final List<String> TAGS_PRIMERO = List.of("Consulta Bridgecore", "Fulfillment", "Reproceso Completo Asíncrono", "Envio Directo Fachada", "Liberar memoria");

    // Swagger UI pinta los tags en el orden de la lista; sort estable: el resto conserva su orden
    @Bean
    public OpenApiCustomizer ordenarTags() {
        return openApi -> {
            if (openApi.getTags() != null) {
                openApi.getTags().sort(Comparator.comparingInt(tag -> {
                    int posicion = TAGS_PRIMERO.indexOf(tag.getName());
                    return posicion < 0 ? TAGS_PRIMERO.size() : posicion;
                }));
            }
        };
    }
}
