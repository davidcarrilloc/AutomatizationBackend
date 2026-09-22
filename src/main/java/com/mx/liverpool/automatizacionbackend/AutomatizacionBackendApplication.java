package com.mx.liverpool.automatizacionbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class AutomatizacionBackendApplication extends SpringBootServletInitializer {
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(AutomatizacionBackendApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(AutomatizacionBackendApplication.class, args);
    }
}
