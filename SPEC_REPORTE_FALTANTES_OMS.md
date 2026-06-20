# Spec: Reporte de órdenes OMS faltantes (Liverpool y Suburbia)

> Estado: **Implementado**. Este documento describe la solución final tal como quedó en el código.

## Contexto
Existen transacciones (TX) que se cobraron pero **no llegaron a registrarse en OMS** (no aparecen en
`atgcore.LP_OMS_AUDIT_INFO`). Se necesita un reporte que liste esas TX faltantes y, para cada una,
consulte su estatus real en OMS (Apigee `/oms/v1/int/2010`) reutilizando la misma lógica que ya usan los
endpoints `verificarEnOMSLiverpool` / `verificarEnOMSSuburbia`. El resultado se entrega como Excel `.xlsx`,
uno por canal: Liverpool (LP) y Suburbia (SBB).

## Objetivo
Exponer dos endpoints REST (uno por canal) en `OMSController`, siguiendo los lineamientos de `CLAUDE.md`,
que devuelvan un archivo Excel con las órdenes faltantes y su estatus en OMS.

## Endpoints
Ambos son `GET`, reciben `inicio` y `fin` como **query params obligatorios** (formato ISO `yyyy-MM-dd`) y
devuelven un `.xlsx` como descarga (`Content-Disposition: attachment`).

| Método / Ruta | Canal | Llave a OMS | `enterpriseCode` | Archivo |
|---|---|---|---|---|
| `GET /api/v1/oms/reporte/faltantes/liverpool` | Liverpool | `orden_venta` | `LIVERPOOL` | `Reporte_Faltantes_OMS_LP.xlsx` |
| `GET /api/v1/oms/reporte/faltantes/suburbia` | Suburbia | `atg_ship_grp_id` | `SUBURBIA` | `Reporte_Faltantes_OMS_SBB.xlsx` |

`inicio` se convierte a `inicio.atStartOfDay()` y `fin` a `fin.atTime(23,59,59)` antes de consultar.
Nombres de archivo homologados con los reportes existentes (`Reporte_Verificacion_OMS_LP/SBB.xlsx`).

Ejemplo:
`GET /api/v1/oms/reporte/faltantes/liverpool?inicio=2026-01-01&fin=2026-06-17`

## Flujo
1. Se leen las TX faltantes de BD con la query del canal (LP → esquema `BRIDGECORE`; SBB → `BRIDGECORE2`).
2. Se extrae la llave de cada fila (LP = `orden_venta`, SBB = `atg_ship_grp_id`), se deduplica
   (`LinkedHashSet`) y se pasa por `OMSService.massivePostOrder(ids, enterpriseCode)`.
3. `massivePostOrder` devuelve un `Map<llave, {status, responseBody, orderStatuses}>`.
4. Se arma el Excel cruzando cada fila de BD con su resultado de OMS por la llave; si OMS no devolvió esa
   llave, las tres columnas de OMS quedan vacías.

## Consultas (`src/main/resources/queries.properties`)
Dos claves, ambas con el rango de fechas parametrizado (`tip.FECHA_TX_COMPRA BETWEEN :inicio AND :fin`):

- `consulta.tx-no-oms-audit-sbb` → esquema **`BRIDGECORE2`**, `tx.id_tipo_articulo IN ('0', '1')` (BT, SL).
- `consulta.tx-no-oms-audit-lp` → esquema **`BRIDGECORE`**, `tx.id_tipo_articulo IN ('0')` (BT).

Filtros comunes: `tip.id_cat_estatus = 0`, `tx.id_tipo_tx = 1`, `atg_ship_grp_id IS NOT NULL AND LIKE 'sg%'`,
`total_cobrado > 0`, y `NOT EXISTS` contra `atgcore.LP_OMS_AUDIT_INFO@catalog_link_pro` por
`SHIPPING_GROUP_ID` (esto es lo que define "faltante en OMS").

## Columnas del Excel (en orden)
`atg_order_id`, `atg_ship_grp_id`, `Status en OMS`, `Response Body`, `OrderStatuses`, `FECHA_TX_COMPRA`,
`Diferencia`, `error_detail`, `id_tipo_tx`, `orden_venta`, `id`, `id_cat_estatus`, `pedido`, `boleta`,
`terminal`, `remision`, `total_cobrado`, `total_original`, `is_mkp`, `zip_code`, `recognition_store`,
`recognition_store_channel`, `recognition_store_sub_channel`, `tienda_cliente`.

- `Status en OMS`, `Response Body`, `OrderStatuses` provienen de `massivePostOrder` (igual que en
  `verificarEnOMS...`).
- `is_mkp` y `tienda_cliente` solo se pueblan en **LP**; en SBB salen vacías (ver hallazgos).

## Componentes implementados
- `model/OmsFaltante.java` — mapeo de la fila (BeanPropertyRowMapper). `diferencia` es `String`.
- `repository/FaltantesOmsRepository.java` — `@Qualifier("bridgeCoreDataSource")`; métodos
  `obtenerFaltantesLiverpool(inicio, fin)` y `obtenerFaltantesSuburbia(inicio, fin)`.
- `service/FaltantesOmsService.java` — orquesta BD → `massivePostOrder` → Excel; métodos
  `generarReporteLiverpool(...)` y `generarReporteSuburbia(...)` que devuelven `byte[]`.
- `service/ExcelService.java` — método `crearReporteFaltantes(filas, omsResult, llave)` (reutiliza
  `truncarCelda`).
- `controller/OMSController.java` — los dos endpoints GET.
- `OMSService.massivePostOrder(...)` — reutilizado sin cambios.

## Hallazgos y decisiones (gotchas resueltos)
1. **Comentarios `--` inline rompen las queries del `.properties`.** Las líneas continuadas con `\` se
   unen en una sola; un comentario SQL `--` comenta el resto de la consulta. Se eliminaron los `-- BT/SL`.
2. **`BRIDGECORE2` no tiene `is_mkp` ni `tienda_cliente`.** La query SBB omite ambas columnas del SELECT;
   en el Excel esas celdas quedan vacías para SBB (el encabezado se mantiene igual en ambos reportes).
3. **`(fecha_fin_tx - fecha_ini_tx)` en `BRIDGECORE` es INTERVAL DAY TO SECOND** (columnas TIMESTAMP), no
   un número. Mapear `diferencia` como `BigDecimal` lanzaba `ORA-17004` (`getBigDecimal not implemented for
   T4CIntervaldsAccessor`). Se mapea como **`String`** (`getString` sirve para intervalo y número).
4. **Se removió `customer_email`** del SELECT de ambas queries junto con el `INNER JOIN ... tx_cliente tc`
   (ya no se usa el alias `tc`); también se quitó del modelo y del Excel.
5. **Acceso a esquemas:** `bridgeCoreDataSource` (usuario `USR_VENTA_ENTREGA` @ APPSPRO) ve tanto
   `BRIDGECORE` como `BRIDGECORE2`, por lo que un único datasource sirve para ambos canales.

## Fuera de alcance
- NO modificar el esquema de la base de datos.
- NO añadir paginación.
- NO tocar otros endpoints ni el frontend.

## Verificación
- `mvn clean package -DskipTests` compila (usar JDK 23).
- Swagger `http://localhost:9091/swagger-ui/index.html`: invocar ambos endpoints con un rango de fechas y
  confirmar que descargan el `.xlsx` con el encabezado en el orden indicado y las columnas de OMS pobladas.
