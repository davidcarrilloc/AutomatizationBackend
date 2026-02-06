package com.mx.liverpool.automatizacionbackend.configuration;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class FormatterConfig {
    @Bean
    public DataFormatter dataFormatter() {
        return new DataFormatter(new Locale("es", "MX"));
    }
}
