package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.CorreoTx;
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
public class CorreoTxRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String consultaTxPorCorreo;

    @Autowired
    public CorreoTxRepository(
            @Qualifier("bridgeCoreDataSource") DataSource bridgeCoreDataSource,
            @Value("${consulta.tx-por-correo}") String consultaTxPorCorreo) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(bridgeCoreDataSource);
        this.consultaTxPorCorreo = consultaTxPorCorreo;
    }

    public List<CorreoTx> obtenerTxPorCorreos(List<String> correos) {
        Map<String, Object> params = new HashMap<>();
        params.put("correos", correos);
        return jdbcTemplate.query(consultaTxPorCorreo, params, new BeanPropertyRowMapper<>(CorreoTx.class));
    }
}
