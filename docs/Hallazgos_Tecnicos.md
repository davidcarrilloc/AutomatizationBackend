# Hallazgos técnicos

Tropiezos ya resueltos en este proyecto y por qué se resolvieron así. Cuestan horas de redescubrir; antes de pelearte con algo de esta lista, léela.

## Oracle / queries

**Los comentarios `--` inline rompen las queries de `queries.properties`.**
Las líneas continuadas con `\` se unen en una sola línea al cargarse. Un comentario SQL `--` entonces comenta *el resto de la consulta*, no sólo su línea. No uses `--` dentro de una query del `.properties`.

**`(fecha_fin_tx - fecha_ini_tx)` en `BRIDGECORE` es INTERVAL DAY TO SECOND, no un número.**
Las columnas son TIMESTAMP, así que la resta da un intervalo. Mapear ese campo como `BigDecimal` lanza `ORA-17004` (`getBigDecimal not implemented for T4CIntervaldsAccessor`). Mapéalo como **`String`** — `getString` funciona tanto para intervalo como para número.

**`BRIDGECORE2` no tiene las columnas `is_mkp` ni `tienda_cliente`.**
La query de Suburbia las omite del SELECT; en el Excel esas celdas quedan vacías para SBB. El encabezado se mantiene igual en ambos reportes (LP y SBB) a propósito.

**Un solo datasource sirve para ambos esquemas.**
`bridgeCoreDataSource` (usuario `USR_VENTA_ENTREGA` @ APPSPRO) ve tanto `BRIDGECORE` como `BRIDGECORE2`. No hace falta un datasource extra para Suburbia.

**`customer_email` se removió** del SELECT de las queries de faltantes OMS, junto con el `INNER JOIN ... tx_cliente tc`. Ya no se usa el alias `tc`.

## Spring

**`@Async` no aplica en llamadas internas.**
Si un método `@Async` se llama con `this.metodo(...)` desde la misma clase, la llamada se salta el proxy de Spring y corre síncrona. La solución usada en `FulfillmentService` es auto-inyectarse: `@Lazy FulfillmentService self` y llamar `self.procesarJob(...)`.

## Excel (Apache POI)

**Límite de celda: 32 767 caracteres.** Pasarse lanza excepción y tumba la generación del reporte. Todo lo que pueda venir largo (JSON o XML de respuesta) va por `truncarCelda` en `ExcelService`.
