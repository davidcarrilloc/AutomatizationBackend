package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.NoOMS;
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
public class OMSRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final String consultaNoOMS;

    @Autowired
    public OMSRepository(@Qualifier("atgCoreDataSource") DataSource namedParameterJdbcTemplate,
                                   @Value("${consulta.no-oms}") String consultaNoOMS) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(namedParameterJdbcTemplate);
        this.consultaNoOMS = consultaNoOMS;
    }

    public List<NoOMS> obtenerOrdenesNoOMS(LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        Map<String, Object> params = new HashMap<>();
        params.put("inicio", fechaInicio);
        params.put("fin", fechaFin);

        return namedParameterJdbcTemplate.query(
                consultaNoOMS,
                params,
                new BeanPropertyRowMapper<>(NoOMS.class)
        );
    }
}