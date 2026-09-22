package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.ConteoValidacionSl;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Log4j2
public class ValidacionSlRepository {
    private final JdbcTemplate jdbcTemplate;
    private final String insertarConteo;
    private final String consultaConteos;

    @Autowired
    public ValidacionSlRepository(@Qualifier("sqliteJdbcTemplate") JdbcTemplate jdbcTemplate,
                                  @Value("${crea.tabla-validacion-sl}") String crearTabla,
                                  @Value("${inserta.validacion-sl}") String insertarConteo,
                                  @Value("${consulta.validacion-sl}") String consultaConteos) {
        this.jdbcTemplate = jdbcTemplate;
        this.insertarConteo = insertarConteo;
        this.consultaConteos = consultaConteos;
        jdbcTemplate.execute(crearTabla);
    }

    public void guardarConteo(String fecha, int existen, int noExisten, int historico) {
        jdbcTemplate.update(insertarConteo, fecha, existen, noExisten, historico);
    }

    public List<ConteoValidacionSl> obtenerUltimosDias(int dias) {
        return jdbcTemplate.query(consultaConteos, new BeanPropertyRowMapper<>(ConteoValidacionSl.class), dias);
    }
}
