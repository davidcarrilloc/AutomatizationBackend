package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.OmsFaltante;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Log4j2
public class FaltantesOmsRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String consultaFaltantesLiverpool;
    private final String consultaFaltantesSuburbia;

    @Autowired
    public FaltantesOmsRepository(
            @Qualifier("bridgeCoreDataSource") DataSource bridgeCoreDataSource,
            @Value("${consulta.tx-no-oms-audit-lp}") String consultaFaltantesLiverpool,
            @Value("${consulta.tx-no-oms-audit-sbb}") String consultaFaltantesSuburbia) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(bridgeCoreDataSource);
        this.consultaFaltantesLiverpool = consultaFaltantesLiverpool;
        this.consultaFaltantesSuburbia = consultaFaltantesSuburbia;
    }

    public List<OmsFaltante> obtenerFaltantesLiverpool(LocalDateTime inicio, LocalDateTime fin) {
        return jdbcTemplate.query(consultaFaltantesLiverpool, construirParametros(inicio, fin),
                new BeanPropertyRowMapper<>(OmsFaltante.class));
    }

    public List<OmsFaltante> obtenerFaltantesSuburbia(LocalDateTime inicio, LocalDateTime fin) {
        return jdbcTemplate.query(consultaFaltantesSuburbia, construirParametros(inicio, fin),
                new BeanPropertyRowMapper<>(OmsFaltante.class));
    }

    private Map<String, Object> construirParametros(LocalDateTime inicio, LocalDateTime fin) {
        Map<String, Object> params = new HashMap<>();
        params.put("inicio", inicio);
        params.put("fin", fin);
        return params;
    }
}
