package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.ClienteRemision;
import com.mx.liverpool.automatizacionbackend.model.DetalleSkuRemision;
import com.mx.liverpool.automatizacionbackend.model.Remision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RemisionRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final String consultaCobroRemision;
    private final String consultaDetalleSkuRemision;
    private final String consultaClienteRemision;
    private final String consultaBcNoOms;

    @Autowired
    public RemisionRepository(@Qualifier("bridgeCoreDataSource") DataSource namedParameterJdbcTemplate,
                                   @Value("${consulta.check-cobro-remision}") String consultaCobroRemision,
                                   @Value("${consulta.detalle-sku-remision}") String consultaDetalleSkuRemision,
                                   @Value("${consulta.cliente-remision}") String consultaClienteRemision,
                                   @Value("${consulta.bc-nooms}") String consultaBcNoOms) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(namedParameterJdbcTemplate);
        this.consultaCobroRemision = consultaCobroRemision;
        this.consultaDetalleSkuRemision = consultaDetalleSkuRemision;
        this.consultaClienteRemision = consultaClienteRemision;
        this.consultaBcNoOms = consultaBcNoOms;
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

    // La remisión se ata como VARCHAR: BRIDGECORE la guarda como texto y una conversión a número
    // perdería los ceros a la izquierda de las remisiones que sí los traen.
    public List<DetalleSkuRemision> obtenerDetalleSkuRemision(String remision) {
        return namedParameterJdbcTemplate.query(
                consultaDetalleSkuRemision,
                new MapSqlParameterSource().addValue("remision", remision, Types.VARCHAR),
                new BeanPropertyRowMapper<>(DetalleSkuRemision.class)
        );
    }

    // Correo y nombre del cliente, sin el detalle de SKUs: una remisión sin partidas igual los devuelve.
    public List<ClienteRemision> obtenerClienteRemision(String remision) {
        return namedParameterJdbcTemplate.query(
                consultaClienteRemision,
                new MapSqlParameterSource().addValue("remision", remision, Types.VARCHAR),
                new BeanPropertyRowMapper<>(ClienteRemision.class)
        );
    }

    // Volcado de transacciones no enviadas a OMS por remisión. Devuelve Map (no modelo tipado): son 46
    // columnas de tx_informacion_procesada y queryForList preserva el orden del SELECT. El troceo de las
    // remisiones en lotes de 1000 (límite del IN de Oracle) lo hace el servicio.
    public List<Map<String, Object>> obtenerBcNoOms(List<String> remisiones) {
        Map<String, Object> params = new HashMap<>();
        params.put("remisiones", remisiones);

        return namedParameterJdbcTemplate.queryForList(consultaBcNoOms, params);
    }
}
