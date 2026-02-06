package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.CodigoDigital;
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
public class CodigoDigitalRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final String consultaObtenerCodigosDigitales;

    @Autowired
    public CodigoDigitalRepository(@Qualifier("atgCoreDataSource") DataSource namedParameterJdbcTemplate,
                                   @Value("${consulta.obtener-codigos-digitales}") String consultaObtenerCodigosDigitales) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(namedParameterJdbcTemplate);
        this.consultaObtenerCodigosDigitales = consultaObtenerCodigosDigitales;
    }

    public List<CodigoDigital> obtenerCodigosDigitales(List<String> referencias) {
        Map<String, Object> params = new HashMap<>();
        params.put("referencias", referencias);

        return namedParameterJdbcTemplate.query(
                consultaObtenerCodigosDigitales,
                params,
                new BeanPropertyRowMapper<>(CodigoDigital.class)
        );
    }
}
