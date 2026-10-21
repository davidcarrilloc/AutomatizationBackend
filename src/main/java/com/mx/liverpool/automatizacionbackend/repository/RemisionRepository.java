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
    private final String consultaDetalleSkuOrdenVenta;
    private final String consultaDetalleSkuShippingGroup;
    private final String consultaClienteOrdenVenta;
    private final String consultaClienteShippingGroup;
    private final String consultaBcNoOms;
    private final String consultaBcNoOmsOrdenVenta;
    private final String consultaBcNoOmsShippingGroup;

    @Autowired
    public RemisionRepository(@Qualifier("bridgeCoreDataSource") DataSource namedParameterJdbcTemplate,
                                   @Value("${consulta.check-cobro-remision}") String consultaCobroRemision,
                                   @Value("${consulta.detalle-sku-remision}") String consultaDetalleSkuRemision,
                                   @Value("${consulta.cliente-remision}") String consultaClienteRemision,
                                   @Value("${consulta.detalle-sku-orden-venta}") String consultaDetalleSkuOrdenVenta,
                                   @Value("${consulta.detalle-sku-shipping-group}") String consultaDetalleSkuShippingGroup,
                                   @Value("${consulta.cliente-orden-venta}") String consultaClienteOrdenVenta,
                                   @Value("${consulta.cliente-shipping-group}") String consultaClienteShippingGroup,
                                   @Value("${consulta.bc-nooms}") String consultaBcNoOms,
                                   @Value("${consulta.bc-nooms-orden-venta}") String consultaBcNoOmsOrdenVenta,
                                   @Value("${consulta.bc-nooms-shipping-group}") String consultaBcNoOmsShippingGroup) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(namedParameterJdbcTemplate);
        this.consultaCobroRemision = consultaCobroRemision;
        this.consultaDetalleSkuRemision = consultaDetalleSkuRemision;
        this.consultaClienteRemision = consultaClienteRemision;
        this.consultaDetalleSkuOrdenVenta = consultaDetalleSkuOrdenVenta;
        this.consultaDetalleSkuShippingGroup = consultaDetalleSkuShippingGroup;
        this.consultaClienteOrdenVenta = consultaClienteOrdenVenta;
        this.consultaClienteShippingGroup = consultaClienteShippingGroup;
        this.consultaBcNoOms = consultaBcNoOms;
        this.consultaBcNoOmsOrdenVenta = consultaBcNoOmsOrdenVenta;
        this.consultaBcNoOmsShippingGroup = consultaBcNoOmsShippingGroup;
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

    // La llave decide la columna del WHERE: sg… → atg_ship_grp_id, 98… → orden_venta, resto → remision.
    private static String elegir(String llave, String porRemision, String porOrdenVenta, String porShippingGroup) {
        if (llave.regionMatches(true, 0, "sg", 0, 2)) return porShippingGroup;
        if (llave.startsWith("98")) return porOrdenVenta;
        return porRemision;
    }

    // La remisión se ata como VARCHAR: BRIDGECORE la guarda como texto y una conversión a número
    // perdería los ceros a la izquierda de las remisiones que sí los traen.
    public List<DetalleSkuRemision> obtenerDetalleSkuRemision(String remision) {
        return namedParameterJdbcTemplate.query(
                elegir(remision, consultaDetalleSkuRemision, consultaDetalleSkuOrdenVenta, consultaDetalleSkuShippingGroup),
                new MapSqlParameterSource().addValue("remision", remision, Types.VARCHAR),
                new BeanPropertyRowMapper<>(DetalleSkuRemision.class)
        );
    }

    // Correo y nombre del cliente, sin el detalle de SKUs: una remisión sin partidas igual los devuelve.
    public List<ClienteRemision> obtenerClienteRemision(String remision) {
        return namedParameterJdbcTemplate.query(
                elegir(remision, consultaClienteRemision, consultaClienteOrdenVenta, consultaClienteShippingGroup),
                new MapSqlParameterSource().addValue("remision", remision, Types.VARCHAR),
                new BeanPropertyRowMapper<>(ClienteRemision.class)
        );
    }

    // Volcado de transacciones no enviadas a OMS por remisión. Devuelve Map (no modelo tipado): son 46
    // columnas de tx_informacion_procesada y queryForList preserva el orden del SELECT. El troceo de las
    // remisiones en lotes de 1000 (límite del IN de Oracle) lo hace el servicio.
    public List<Map<String, Object>> obtenerBcNoOms(List<String> remisiones) {
        return obtenerVolcadoBc(consultaBcNoOms, remisiones);
    }

    public List<Map<String, Object>> obtenerBcNoOmsPorOrdenVenta(List<String> ordenesVenta) {
        return obtenerVolcadoBc(consultaBcNoOmsOrdenVenta, ordenesVenta);
    }

    public List<Map<String, Object>> obtenerBcNoOmsPorShippingGroup(List<String> shippingGroups) {
        return obtenerVolcadoBc(consultaBcNoOmsShippingGroup, shippingGroups);
    }

    // Las tres consultas comparten el SELECT de consulta.bc-nooms-base; solo cambia la columna del IN.
    private List<Map<String, Object>> obtenerVolcadoBc(String consulta, List<String> valores) {
        Map<String, Object> params = new HashMap<>();
        params.put("valores", valores);

        return namedParameterJdbcTemplate.queryForList(consulta, params);
    }
}
