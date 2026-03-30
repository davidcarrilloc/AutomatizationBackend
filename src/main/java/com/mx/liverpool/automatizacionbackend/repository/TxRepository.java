package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.CobroRow;
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
public class TxRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final String consultaCobro;

    @Autowired
    public TxRepository(@Qualifier("bridgeCoreDataSource") DataSource namedParameterJdbcTemplate,
                              @Value("${consulta.cobro}") String consultaCobro) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(namedParameterJdbcTemplate);
        this.consultaCobro = consultaCobro;
    }

    public List<CobroRow> obtenerCobroShippingGroup(String atgOrderId, String shippingGroupId) {
        Map<String, Object> params = new HashMap<>();
        params.put("atgShipGrpIds", shippingGroupId);
        params.put("atgOrderIds", atgOrderId);

        return namedParameterJdbcTemplate.query(
                consultaCobro,
                params,
                new BeanPropertyRowMapper<>(CobroRow.class)
        );
    }
}
