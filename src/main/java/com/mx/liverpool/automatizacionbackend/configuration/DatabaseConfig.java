package com.mx.liverpool.automatizacionbackend.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {
    @Bean(name = "bridgeCoreDataSource")
    @ConfigurationProperties(prefix = "bridgecore.datasource")
    public DataSource bridgeCoreDataSource() {
        return DataSourceBuilder.create().build();
    }
}
