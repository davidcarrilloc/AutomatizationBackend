package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.TxPorMinuto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SQLiteRepository {
    private final JdbcTemplate jdbcTemplate;
    private final String consultaCrearTabla;
    private final String insertarInformacion;

    @Autowired
    public SQLiteRepository(@Qualifier("sqliteJdbcTemplate") JdbcTemplate jdbcTemplate,
                            @Value("${crea.tabla}") String consultaCrearTabla,
                            @Value("${inserta.informacion}") String insertarInformacion) {
        this.jdbcTemplate = jdbcTemplate;
        this.consultaCrearTabla = consultaCrearTabla;
        this.insertarInformacion = insertarInformacion;
    }

    public void crearTabla() {
        jdbcTemplate.execute(consultaCrearTabla);
    }

    public void insertarInformacion(List<TxPorMinuto> txPorMinuto) {
        jdbcTemplate.batchUpdate(insertarInformacion, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TxPorMinuto log = txPorMinuto.get(i);

                if (log.getCanal() == null) return;

                ps.setString(1, log.getCanal());
                ps.setString(2, log.getSitio());
                ps.setString(3, log.getTruncdate());
                ps.setInt(4, log.getTransacciones());
                ps.setDouble(5, log.getTotalCobrado());
            }

            @Override
            public int getBatchSize() {
                return txPorMinuto.size();
            }
        });
    }
}
