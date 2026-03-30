package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.AtgMarketplace;
import com.mx.liverpool.automatizacionbackend.model.OfferId;
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
public class AtgMirklRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final String consultaCobroExistenciaOfferId;
    private final String consultaBridgecoreDevolucionApv;

    @Autowired
    public AtgMirklRepository(@Qualifier("atgCoreDataSource") DataSource namedParameterJdbcTemplate,
                              @Value("${consulta.check-existencia-offerId}") String consultaCobroExistenciaOfferId,
                              @Value("${consulta.consulta-bridgecore-devolucion-apv}") String consultaBridgecoreDevolucionApv) {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(namedParameterJdbcTemplate);
        this.consultaCobroExistenciaOfferId = consultaCobroExistenciaOfferId;
        this.consultaBridgecoreDevolucionApv = consultaBridgecoreDevolucionApv;
    }

    public List<OfferId> obtenerExistenciaOfferIds(List<String> offerIds) {
        Map<String, Object> params = new HashMap<>();
        params.put("offerIds", offerIds);

        return namedParameterJdbcTemplate.query(
                consultaCobroExistenciaOfferId,
                params,
                new BeanPropertyRowMapper<>(OfferId.class)
        );
    }

    public List<AtgMarketplace> obtenerDatosBridgecoreDevolucionApv(List<String> remisiones) {
        Map<String, Object> params = new HashMap<>();
        params.put("remisiones", remisiones);

        return namedParameterJdbcTemplate.query(
                consultaBridgecoreDevolucionApv,
                params,
                new BeanPropertyRowMapper<>(AtgMarketplace.class)
        );
    }
}
