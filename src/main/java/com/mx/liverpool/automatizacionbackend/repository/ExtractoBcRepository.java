package com.mx.liverpool.automatizacionbackend.repository;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Log4j2
public class ExtractoBcRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String consultaLiverpoolSl;

    @Autowired
    public ExtractoBcRepository(
            @Qualifier("bridgeCoreDataSource") DataSource bridgeCoreDataSource,
            @Value("${consulta.extracto-bc-lp-sl}") String consultaLiverpoolSl) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(bridgeCoreDataSource);
        this.consultaLiverpoolSl = consultaLiverpoolSl;
    }

    public List<String> obtenerRemisionesLiverpoolSl(LocalDateTime inicio, LocalDateTime fin) {
        return jdbcTemplate.queryForList(consultaLiverpoolSl, construirParametros(inicio, fin), String.class);
    }

    private Map<String, Object> construirParametros(LocalDateTime inicio, LocalDateTime fin) {
        Map<String, Object> params = new HashMap<>();
        params.put("inicio", inicio);
        params.put("fin", fin);
        return params;
    }
}
