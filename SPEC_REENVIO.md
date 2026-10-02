# SPEC: <módulo>

> Estado: **Implementado**

Necesito que crees una automatización con las siguientes condiciones:
Sacar el listado de remisiones dia por dia hasta mediados de junio, y dejar un registro en db de por que dias pasaste
Aquellas que den success enlistarlas, las que den "error" igual enlistalas en el mismo excel de salida

- Vas a pasar por todo el flujo, capas y cláusulas necesarias establecidas en CLAUDE.md, no apliques marcas de agua o commments

## Brief

**Por qué:** Cada dia tenemos un porcentaje 1% a 10% de remisiones que no pasan por el fulfillment automaticamente, necesito que todas las cobradas dia por dia (para que sea mas sencillo y llevar una bitacora de por que dias ya pasamos de 00 a 24 hrs) y se reenvien con el fulfillment proceso que ya tenemos
No se debe encolar en el mismo proceso, debe ser otro totalmente distinto, ocupa brianstorming para ver que endpoints pueden salir y como podemos mostrar los avances al usuario

**Entrada:** Este es un proceso que se ejecuta desde el swagger, sin entrada, va dia por dia y si se interrumpe va hasta el ultimo dia guardado en la bitacora y sigue
Tiene que ir desde hoy a dias anteriores hasya mediados de junio, por ejemplo desde el dia de ejecucion 29/09/2026 hasta 15/06/2026

**Origen:** Usa brainstorming para crear un nuevo query que sirva para este caso, el que tengo hoy de dia a dia es:
!! IMPORTANTE SE VA A REPROCESAR BIG TICKET, SOFTLINE TANTO DE LIVERPOOL COMO DE SUBURBIA (BRIDGECORE2)
SELECT
tip.atg_ship_grp_id,
tip.atg_order_id,
tip.orden_venta,
tip.remision,
tip.*
-- cat.response_status
FROM BRIDGECORE.tx_informacion_procesada tip
INNER JOIN  BRIDGECORE.tx tx on tip.id = tx.id_info_procesada
-- LEFT JOIN atgcore.LP_OMS_AUDIT_INFO@catalog_link_pro cat ON cat.ORDER_ID = tip.atg_order_id
WHERE 1=1
-- and tx.id_tipo_articulo = '0' -- BT
-- and tx.id_tipo_articulo = '1' -- SL
and tx.id_tipo_articulo IN ('0','1')
AND tip.id_cat_estatus=0
AND tx.id_tipo_tx=1
and tip.atg_ship_grp_id is not null -- and tip.atg_ship_grp_id not like 'sg%'
and tip.atg_order_id is not null
and tip.total_cobrado > 0
-- and tip.tienda_cliente in (39)
-- AND tip.is_click_and_collct = 'N'
and tip.FECHA_TX_COMPRA BETWEEN TO_DATE('18-09-26 09:00:01', 'DD-MM-YY HH24:MI:SS') and TO_DATE('18-09-26 23:59:59', 'DD-MM-YY HH24:MI:SS')
order by tip.FECHA_TX_COMPRA asc
;

**Regla:** Debe ser eficiente y visualizar el avance el usuario

**Salida:** Igual que el fulfillment actual
!! Importante si puedes agregar tambien los "failure" sirve, para tenerlos como analisis a posteriori, un failure se ve asi: "statusMkp":"FAILURE", el mas importante
es si llega a estar en "statusMkp":"FAILURE" o "statusOms":"FAILURE" estos dos casos los debes enlistar como igual que el success o el "error"
- Errores
- Trunca lo que pase el límite de Excel (32 767 car.)

Sólo si aplica:
- **Ritmo:** como el fulfillment actual pero sin tiempo de espera, con la regla de error y 3 segundos de espera y encolar al final
- **Job asíncrono:** Se debe saber estatus, pero usa brianstorming para una mejora de visibilidad ya que va a ser un reproceso masivo de varios meses
- **Reutiliza:** Fulfillment

## Skills

Marca las que apliquen y borra el resto. Cada una carga un patrón ya resuelto en este repo para no
volver a derivarlo; `CLAUDE.md` sigue mandando en las convenciones de cada capa.

- **Caso C — el spec consulta base de datos** (Oracle o la SQLite local) → **`consulta-oracle`**.
  Qué datasource tiene qué tabla, el formato de `queries.properties` y sus trampas, molde de
  repository.

## Cierre (al terminar — esto es lo que vuelve el spec documentación)

**Componentes:**
- `controller/ReenvioController` — POST `/ejecutar`, POST `/reiniciar`, GET `/estatus`, GET `/bitacora`, GET `/excel` bajo `/api/v1/reenvio`.
- `service/ReenvioService` — play/pausa, reinicio con `join`, recorrido de días pendientes, rondas de diferidos, clasificación.
- `repository/ReenvioRepository` — query Oracle del día (BRIDGECORE + BRIDGECORE2) y tablas SQLite `reenvio_bitacora`/`reenvio_resultado`.
- `model/ReenvioResultado`, `model/ReenvioBitacora`, `payload/response/EstatusReenvio`.
- `service/FulfillmentService` — `consultarFulfillment`, `esErrorGateway` y `rellenarDiezDigitos` pasan a `public`.
- `service/ExcelService.crearReporteReenvio` — SXSSF, 6 columnas con `truncarCelda`.
- `configuration/AsyncConfig.reenvioExecutor`; `application.properties` `reenvio.fecha-limite=2026-06-15`; 11 claves en `queries.properties`.
- `docs/Documentacion_Endpoints.md` y `docs/Hallazgos_Tecnicos.md`.

**Hallazgos:** en `docs/Hallazgos_Tecnicos.md` § Reenvío (ReenvioService): ventana `>= / <`, play/pausa por `CompletableFuture`, `join` en reiniciar, rango abierto en SQLite, SXSSF, `tracking_number` de BRIDGECORE2 sin verificar.

**Verificación:** build con JDK 23 OK. Harness en scratchpad: 15 casos de `clasificar` y `diasPendientes`, y 7 casos del repositorio contra un SQLite temporal (creación idempotente, rango abierto/1 día, orden, borrado por día, upsert de bitácora). Pendiente E2E en servidor (Oracle no accesible desde dev): query real, ejecutar/detener/reanudar, reiniciar en corrida, Excel de 1 día.
