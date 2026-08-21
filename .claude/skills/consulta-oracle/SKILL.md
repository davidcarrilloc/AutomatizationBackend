---
name: consulta-oracle
description: Úsala cuando el SPEC pida datos de base de datos en este backend — una consulta nueva a Oracle (BRIDGECORE, ATG, QA2) o a la SQLite local — o cuando una query de queries.properties devuelva vacío, truene con ORA-17004, o no sepas qué datasource tiene la tabla que buscas. Cubre los 4 datasources y qué vive en cada uno, el formato de queries.properties con sus trampas, y el molde de repository.
---

# Consultar base de datos

El proyecto **no usa JPA**: todo es `NamedParameterJdbcTemplate` sobre SQL escrito a mano, y **el SQL
nunca va en el código Java** — vive en `src/main/resources/queries.properties` y se inyecta con
`@Value`.

## 1. Qué datasource tiene qué

| Qualifier | Base | Qué vive ahí |
|---|---|---|
| `bridgeCoreDataSource` | Oracle `APPSPRO` (172.17.212.224) | Transacciones de e-commerce. Ve **`BRIDGECORE` y `BRIDGECORE2`** (Suburbia) con el mismo usuario: no hace falta un datasource extra para SBB |
| `bridgeCoreQA2DataSource` | Oracle `APPSQ` (172.16.212.22) | Ambiente QA. Ojo: el bean lleva `QA2` en mayúsculas |
| `atgCoreDataSource` | Oracle `atgpro` (172.17.212.7) | Catálogo ATG, códigos digitales, marketplace |
| `sqliteDataSource` | Archivo local `VyE.db` | Métricas de TX. **El único escribible**; los Oracle son de solo lectura para esta app |

## 2. La query va en `queries.properties`

Clave en kebab-case con prefijo `consulta.`:

```properties
consulta.mi-operacion=SELECT campo_uno, \
    campo_dos \
    FROM esquema.tabla \
    WHERE 1=1 \
    AND referencia IN (:referencias)
```

- Continuación de línea con `\` al final (y un espacio antes, o las palabras se pegan).
- Parámetros con nombre `:param`, nunca concatenación.
- **Nunca uses comentarios `--`.** Las líneas continuadas se unen en **una sola** al cargar el
  `.properties`, así que un `--` comenta *el resto de la consulta entera*, no su línea. Este es el
  motivo #1 de "la query devuelve vacío y el SQL se ve bien". También en
  `docs/Hallazgos_Tecnicos.md`.

## 3. El repository

Constructor `@Autowired` **explícito** (no `@RequiredArgsConstructor`: hacen falta `@Qualifier` y
`@Value`). Se inyecta el `DataSource` y se envuelve:

```java
@Repository
public class MiRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String consultaMiOperacion;

    @Autowired
    public MiRepository(@Qualifier("bridgeCoreDataSource") DataSource dataSource,
                        @Value("${consulta.mi-operacion}") String consultaMiOperacion) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.consultaMiOperacion = consultaMiOperacion;
    }

    public List<MiModelo> obtenerMiOperacion(List<String> referencias) {
        Map<String, Object> params = new HashMap<>();
        params.put("referencias", referencias);
        return jdbcTemplate.query(consultaMiOperacion, params, new BeanPropertyRowMapper<>(MiModelo.class));
    }
}
```

Moldes vivos: `TxRepository` (dos datasources en el mismo repositorio), `OMSRepository`,
`CodigoDigitalRepository`.

- Los métodos empiezan con `obtener`. Los repositorios son de solo lectura salvo los de SQLite.
- `BeanPropertyRowMapper` mapea `SNAKE_CASE` → `camelCase` solo: el campo del modelo debe llamarse
  igual que la columna en camelCase, o llega `null` sin avisar.

## 4. Trampas de tipos

- **`(fecha_fin_tx - fecha_ini_tx)` en `BRIDGECORE` no es un número.** Las columnas son `TIMESTAMP`,
  así que la resta da un `INTERVAL DAY TO SECOND`. Mapearlo a `BigDecimal` lanza
  `ORA-17004: getBigDecimal not implemented for T4CIntervaldsAccessor`. Mapéalo a **`String`**:
  `getString` sirve tanto para intervalo como para número.
- **`BRIDGECORE2` no tiene `is_mkp` ni `tienda_cliente`.** La query de Suburbia las omite y esas
  celdas quedan vacías en el reporte, a propósito, para que el encabezado sea igual en LP y SBB.

## 5. Antes de escribir una query nueva

Revisa `queries.properties` primero: son ~40 consultas y varias ya traen lo que buscas con otro
filtro. Y comprueba que la tabla esté en el datasource que crees — un `SELECT` contra el esquema
equivocado no falla con "no existe", devuelve vacío.
