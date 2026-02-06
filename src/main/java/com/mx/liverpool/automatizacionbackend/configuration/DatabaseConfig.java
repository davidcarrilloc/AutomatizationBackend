package com.mx.liverpool.automatizacionbackend.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {
    @Primary
    @Bean(name = "bridgeCoreDataSource")
    @ConfigurationProperties(prefix = "bridgecore.datasource")
    public DataSource bridgeCoreDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "bridgeCoreJdbcTemplate")
    public JdbcTemplate bridgeCoreJdbcTemplate(@Qualifier("bridgeCoreDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean(name = "atgCoreDataSource")
    @ConfigurationProperties(prefix = "atgcore.datasource")
    public DataSource atgCoreDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "atgCoreJdbcTemplate")
    public JdbcTemplate atgCoreJdbcTemplate(@Qualifier("atgCoreDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}