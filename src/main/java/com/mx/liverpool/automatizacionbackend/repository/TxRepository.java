package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.CobroRow;
import com.mx.liverpool.automatizacionbackend.model.SegmentoActual;
import com.mx.liverpool.automatizacionbackend.model.SysDate;
import com.mx.liverpool.automatizacionbackend.model.TxPorMinuto;
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
public class TxRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final String consultaCobro;
    private final String consultaTransacciones;
    private final String consultaSegmentoActual;

    @Autowired
    public TxRepository(@Qualifier("bridgeCoreDataSource") DataSource namedParameterJdbcTemplate,
                        @Value("${consulta.cobro}") String consultaCobro,
                        @Value("${consulta.transacciones}") String consultaTransacciones,
                        @Value("${consulta.segmento-actual}") String consultaSegmentoActual) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(namedParameterJdbcTemplate);
        this.consultaCobro = consultaCobro;
        this.consultaTransacciones = consultaTransacciones;
        this.consultaSegmentoActual = consultaSegmentoActual;
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

    public List<TxPorMinuto> obtenerTransacciones(LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        Map<String, Object> params = new HashMap<>();
        params.put("fechaInicio", fechaInicio);
        params.put("fechaFin", fechaFin);

        return namedParameterJdbcTemplate.query(
                consultaTransacciones,
                params,
                new BeanPropertyRowMapper<>(TxPorMinuto.class)
        );
    }

    public List<SegmentoActual> obtenerSegmentoActual() {
        return namedParameterJdbcTemplate.query(
                consultaSegmentoActual,
                new BeanPropertyRowMapper<>(SegmentoActual.class)
        );
    }
}
