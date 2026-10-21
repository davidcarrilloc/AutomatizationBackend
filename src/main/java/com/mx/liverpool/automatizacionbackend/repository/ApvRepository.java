package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.ApvLogOrden;
import lombok.extern.log4j.Log4j2;
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
@Log4j2
public class ApvRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String consultaApvTransactionId;
    private final String consultaApvJsonSterling;

    @Autowired
    public ApvRepository(@Qualifier("apvDataSource") DataSource dataSource,
                         @Value("${consulta.apv-transaction-id}") String consultaApvTransactionId,
                         @Value("${consulta.apv-json-sterling}") String consultaApvJsonSterling) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.consultaApvTransactionId = consultaApvTransactionId;
        this.consultaApvJsonSterling = consultaApvJsonSterling;
    }

    // Del más reciente al más viejo. Máximo 1000 remisiones por llamada (límite del IN de Oracle).
    public List<ApvLogOrden> obtenerTransacciones(List<String> remisiones) {
        Map<String, Object> params = new HashMap<>();
        params.put("remisiones", remisiones);
        return jdbcTemplate.query(consultaApvTransactionId, params, new BeanPropertyRowMapper<>(ApvLogOrden.class));
    }

    // Máximo 1000 transacciones por llamada (límite del IN de Oracle).
    public List<ApvLogOrden> obtenerJsonSterling(List<String> transactionIds) {
        Map<String, Object> params = new HashMap<>();
        params.put("transactionIds", transactionIds);
        return jdbcTemplate.query(consultaApvJsonSterling, params, new BeanPropertyRowMapper<>(ApvLogOrden.class));
    }
}
