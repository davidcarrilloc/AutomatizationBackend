package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.Remision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RemisionRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final String consultaCobroRemision;

    @Autowired
    public RemisionRepository(@Qualifier("bridgeCoreDataSource") DataSource namedParameterJdbcTemplate,
                                   @Value("${consulta.check-cobro-remision}") String consultaCobroRemision) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(namedParameterJdbcTemplate);
        this.consultaCobroRemision = consultaCobroRemision;
    }

    public List<Remision> obtenerCobroRemisiones(List<String> remisiones) {
        Map<String, Object> params = new HashMap<>();
        params.put("remisiones", remisiones);

        return namedParameterJdbcTemplate.query(
                consultaCobroRemision,
                params,
                new BeanPropertyRowMapper<>(Remision.class)
        );
    }
}
