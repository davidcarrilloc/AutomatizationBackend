package com.mx.liverpool.automatizacionbackend.repository;

import com.mx.liverpool.automatizacionbackend.model.ReenvioBitacora;
import com.mx.liverpool.automatizacionbackend.model.ReenvioResultado;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Log4j2
public class ReenvioRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplateSqlite;
    private final String consultaRemisionesDia;
    private final String insertarBitacora;
    private final String insertarResultado;
    private final String borrarResultadosCategoria;
    private final String borrarBitacora;
    private final String consultaProcesadasDia;
    private final String consultaConteoCategorias;
    private final String consultaBitacora;
    private final String consultaResultados;
    private final String consultaConteoResultados;

    @Autowired
    public ReenvioRepository(@Qualifier("bridgeCoreDataSource") DataSource bridgeCoreDataSource,
                             @Qualifier("sqliteDataSource") DataSource sqliteDataSource,
                             @Value("${consulta.reenvio-dia}") String consultaRemisionesDia,
                             @Value("${crea.tabla-reenvio-bitacora}") String crearTablaBitacora,
                             @Value("${crea.tabla-reenvio-resultado}") String crearTablaResultado,
                             @Value("${crea.indice-reenvio-resultado}") String crearIndiceResultado,
                             @Value("${inserta.reenvio-bitacora}") String insertarBitacora,
                             @Value("${inserta.reenvio-resultado}") String insertarResultado,
                             @Value("${borra.reenvio-resultados-categoria}") String borrarResultadosCategoria,
                             @Value("${borra.reenvio-bitacora}") String borrarBitacora,
                             @Value("${consulta.reenvio-procesadas-dia}") String consultaProcesadasDia,
                             @Value("${consulta.reenvio-conteo-categorias}") String consultaConteoCategorias,
                             @Value("${consulta.reenvio-bitacora}") String consultaBitacora,
                             @Value("${consulta.reenvio-resultados}") String consultaResultados,
                             @Value("${consulta.reenvio-conteo-resultados}") String consultaConteoResultados) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(bridgeCoreDataSource);
        this.jdbcTemplateSqlite = new NamedParameterJdbcTemplate(sqliteDataSource);
        this.consultaRemisionesDia = consultaRemisionesDia;
        this.insertarBitacora = insertarBitacora;
        this.insertarResultado = insertarResultado;
        this.borrarResultadosCategoria = borrarResultadosCategoria;
        this.borrarBitacora = borrarBitacora;
        this.consultaProcesadasDia = consultaProcesadasDia;
        this.consultaConteoCategorias = consultaConteoCategorias;
        this.consultaBitacora = consultaBitacora;
        this.consultaResultados = consultaResultados;
        this.consultaConteoResultados = consultaConteoResultados;
        jdbcTemplateSqlite.getJdbcOperations().execute(crearTablaBitacora);
        jdbcTemplateSqlite.getJdbcOperations().execute(crearTablaResultado);
        jdbcTemplateSqlite.getJdbcOperations().execute(crearIndiceResultado);
    }

    public List<ReenvioResultado> obtenerRemisionesDia(LocalDateTime inicio, LocalDateTime fin) {
        Map<String, Object> params = new HashMap<>();
        params.put("inicio", inicio);
        params.put("fin", fin);
        return jdbcTemplate.query(consultaRemisionesDia, params, new BeanPropertyRowMapper<>(ReenvioResultado.class));
    }

    public List<ReenvioBitacora> obtenerBitacora() {
        return jdbcTemplateSqlite.query(consultaBitacora, new BeanPropertyRowMapper<>(ReenvioBitacora.class));
    }

    public List<ReenvioResultado> obtenerResultados(String fechaInicio, String fechaFin) {
        return jdbcTemplateSqlite.query(consultaResultados, construirRango(fechaInicio, fechaFin),
                new BeanPropertyRowMapper<>(ReenvioResultado.class));
    }

    public long obtenerConteoResultados(String fechaInicio, String fechaFin) {
        Long conteo = jdbcTemplateSqlite.queryForObject(consultaConteoResultados, construirRango(fechaInicio, fechaFin), Long.class);
        return conteo == null ? 0 : conteo;
    }

    public void insertarResultado(ReenvioResultado resultado) {
        jdbcTemplateSqlite.update(insertarResultado, new BeanPropertySqlParameterSource(resultado));
    }

    public void insertarBitacora(ReenvioBitacora bitacora) {
        jdbcTemplateSqlite.update(insertarBitacora, new BeanPropertySqlParameterSource(bitacora));
    }

    public List<ReenvioResultado> obtenerProcesadasDia(String fecha) {
        Map<String, Object> params = new HashMap<>();
        params.put("fecha", fecha);
        return jdbcTemplateSqlite.query(consultaProcesadasDia, params, new BeanPropertyRowMapper<>(ReenvioResultado.class));
    }

    public Map<String, Integer> obtenerConteoCategorias() {
        return jdbcTemplateSqlite.queryForList(consultaConteoCategorias, Map.of()).stream()
                .collect(Collectors.toMap(fila -> (String) fila.get("categoria"), fila -> ((Number) fila.get("total")).intValue()));
    }

    public int borrarResultados(List<String> categorias) {
        Map<String, Object> params = new HashMap<>();
        params.put("categorias", categorias);
        return jdbcTemplateSqlite.update(borrarResultadosCategoria, params);
    }

    public int borrarBitacora() {
        return jdbcTemplateSqlite.update(borrarBitacora, Map.of());
    }

    private Map<String, Object> construirRango(String fechaInicio, String fechaFin) {
        Map<String, Object> params = new HashMap<>();
        params.put("fechaInicio", fechaInicio);
        params.put("fechaFin", fechaFin);
        return params;
    }
}
