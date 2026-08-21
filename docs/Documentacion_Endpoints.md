# Documentación de Endpoints — AutomatizacionBackend (VyE)

Backend Spring Boot que automatiza flujos de Venta y Entrega de los sistemas internos de e-commerce de Liverpool.

- **Base del servidor:** `http://localhost:9091`
- **Swagger UI:** `http://localhost:9091/swagger-ui/index.html`
- **Autenticación:** todos los endpoints requieren autenticación (HTTP Basic Auth o Form Login vía Spring Security).
- **Descargas:** los endpoints que devuelven `.xlsx` responden con `Content-Disposition: attachment` y el tipo `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

---

## Upload — `/api/v1/upload`
Carga de archivos para reprocesos de marketplace y consulta de códigos digitales.

### POST `/reprocesoMkpApv`
- **Descripción:** Deposita un CSV en el directorio de procesamiento APV y ejecuta el script Python de reproceso.
- **Qué se requiere:** `multipart/form-data` con un archivo CSV.
- **Qué se obtiene:** `200 OK` con la salida de la ejecución del script.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (CSV) | Sí | Archivo CSV con las órdenes a reprocesar. |

### POST `/reprocesoMkpAtg`
- **Descripción:** Deposita un CSV en el directorio de procesamiento ATG y ejecuta el script Python de reproceso.
- **Qué se requiere:** `multipart/form-data` con un archivo CSV.
- **Qué se obtiene:** `200 OK` con la salida de la ejecución del script.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (CSV) | Sí | Archivo CSV con las órdenes a reprocesar. |

### POST `/cancelacionDevolucionMkpAtg`
- **Descripción:** Procesa cancelaciones/devoluciones de marketplace ATG a partir de un Excel.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (se leen las columnas 0,1,2,7).
- **Qué se obtiene:** `200 OK` con el resultado del proceso de cancelaciones/devoluciones.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) con las cancelaciones/devoluciones. |

### POST `/codigosDigitales`
- **Descripción:** Obtiene los códigos digitales asociados a los identificadores cargados.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (identificadores en la columna A).
- **Qué se obtiene:** `200 OK` con la lista de códigos digitales encontrados.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) con los identificadores en la columna A. |

---

## SQL / Init — `/api/v1/sql`
Inicialización del almacén local SQLite.

### POST `/crearDB`
- **Descripción:** Crea la tabla de métricas de transacciones en el almacén local SQLite.
- **Qué se requiere:** Nada (sin parámetros ni cuerpo).
- **Qué se obtiene:** `200 OK` con el resultado de la creación de la tabla.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| — | — | — | No requiere parámetros. |

---

## TX — `/api/v1/tx`
Detalle y comparativa de transacciones.

### POST `/detalleTx`
- **Descripción:** Devuelve el detalle de una transacción a partir de sus identificadores.
- **Qué se requiere:** Cuerpo JSON `DetalleTxRequest` (todos los campos obligatorios).
- **Qué se obtiene:** `200 OK` con el detalle de la transacción.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `atgOrderId` | string (body) | Sí | Identificador de la orden ATG. Ej. `o12345678`. |
| `atgShippingGroupId` | string (body) | Sí | Identificador del shipping group ATG. Ej. `sg12345678`. |
| `source` | string (body) | Sí | Origen de la transacción. Ej. `LIVERPOOL`. |

### GET `/reporte/diferencia`
- **Descripción:** Compara el volumen de transacciones por hora del día actual contra el día anterior.
- **Qué se requiere:** Nada (sin parámetros).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de la comparativa.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| — | — | — | No requiere parámetros. |

---

## OMS — `/api/v1/oms`
Verificación de órdenes en OMS Liverpool/Suburbia y reportes de faltantes.

### POST `/verificarEnOMSLiverpool`
- **Descripción:** Verifica masivamente en OMS Liverpool las órdenes de un Excel.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (órdenes en la columna A).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de la verificación.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) con las órdenes en la columna A. |

### POST `/verificarEnOMSSuburbia`
- **Descripción:** Verifica masivamente en OMS Suburbia las órdenes de un Excel.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (órdenes en la columna A).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de la verificación.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) con las órdenes en la columna A. |

### GET `/reporte/faltantes/liverpool`
- **Descripción:** Genera el reporte de órdenes no enviadas a OMS Liverpool en un rango de fechas.
- **Qué se requiere:** Parámetros de consulta `inicio` y `fin` (fechas ISO `yyyy-MM-dd`).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de faltantes.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `inicio` | date `yyyy-MM-dd` (query) | Sí | Fecha inicial del rango. Ej. `2026-06-01`. |
| `fin` | date `yyyy-MM-dd` (query) | Sí | Fecha final del rango. Ej. `2026-06-20`. |

### GET `/reporte/faltantes/suburbia`
- **Descripción:** Genera el reporte de órdenes no enviadas a OMS Suburbia en un rango de fechas.
- **Qué se requiere:** Parámetros de consulta `inicio` y `fin` (fechas ISO `yyyy-MM-dd`).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de faltantes.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `inicio` | date `yyyy-MM-dd` (query) | Sí | Fecha inicial del rango. Ej. `2026-06-01`. |
| `fin` | date `yyyy-MM-dd` (query) | Sí | Fecha final del rango. Ej. `2026-06-20`. |

---

## Fulfillment — `/api/v1/fulfillment`
Reproceso asíncrono de fulfillment identificado por `jobId`.

### POST `/reproceso`
- **Descripción:** Inicia un reproceso asíncrono de fulfillment a partir de un Excel.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (identificadores en la columna A).
- **Qué se obtiene:** `202 Accepted` con el `jobId` para consultar estatus y resultados.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) con los identificadores en la columna A. |

### GET `/reproceso/estatus/{jobId}`
- **Descripción:** Consulta el estatus actual del reproceso.
- **Qué se requiere:** `jobId` en la ruta.
- **Qué se obtiene:** `200 OK` con el estatus del job.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `jobId` | string (path) | Sí | Identificador del job de reproceso. |

### GET `/reproceso/jobs`
- **Descripción:** Lista todos los jobs de reproceso registrados desde el último arranque de la aplicación, del más reciente al más viejo. Sirve para recuperar un `jobId` que se perdió y para ver de un vistazo qué sigue corriendo. Los jobs viven en memoria: un reinicio de la aplicación vacía la lista.
- **Qué se requiere:** Nada.
- **Qué se obtiene:** `200 OK` con la lista de jobs, cada uno con `jobId`, `estatus`, `totalTrackings`, `procesados`, `conErrorGateway`, `reprocesados`, `trackingActual`, `inicio` y `fin`. Los que siguen corriendo traen `estatus: EN_PROCESO` y `fin: null`; los terminados, `COMPLETADO` o `COMPLETADO_CON_ERRORES` con su `fin`.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| — | — | — | Ninguno. |

### GET `/reproceso/excel/{jobId}`
- **Descripción:** Descarga los resultados del reproceso.
- **Qué se requiere:** `jobId` en la ruta.
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de resultados.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `jobId` | string (path) | Sí | Identificador del job de reproceso. |

---

## Fulfillment TXT — `/api/v1/fulfillment-txt`
Consulta masiva del `statusOms` de una lista de trackingnumbers, identificada por `jobId`. Golpea el mismo servicio de fulfillment que `/api/v1/fulfillment`, pero devuelve una sola columna de resultado y **varios jobs pueden correr al mismo tiempo**: cada uno se ejecuta en su propio hilo virtual.

### POST `/`
- **Descripción:** Inicia una consulta asíncrona del `statusOms` a partir de un `.txt`. Los trackings se rellenan a 10 dígitos con ceros a la izquierda y se consultan **uno por uno, sin pausas** entre llamadas. El tracking cuya petición falla no detiene la corrida: se encola al final y se reintenta en las rondas de reproceso (`status-oms.max-rondas-reproceso`, 3 por omisión); si tras la última ronda sigue fallando, se conserva su error en el reporte. Las respuestas que sí llegaron del servicio (`UNKNOWN`, `null`, `PARSE_ERROR`) no se reintentan.
- **Qué se requiere:** `multipart/form-data` con un archivo `.txt` con un trackingnumber por línea (las líneas vacías se ignoran).
- **Qué se obtiene:** `202 Accepted` con el estatus inicial del job, incluido el `jobId` para consultar avance y descargar resultados.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (.txt) | Sí | Archivo de texto con un trackingnumber por línea. |

### GET `/estatus/{jobId}`
- **Descripción:** Consulta el avance de la consulta masiva.
- **Qué se requiere:** `jobId` en la ruta.
- **Qué se obtiene:** `200 OK` con `jobId`, `estatus`, `totalTrackings`, `procesados`, `conErrorGateway`, `reprocesados`, `trackingActual`, `inicio` y `fin`. Si el `jobId` no existe, `400`.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `jobId` | string (path) | Sí | Identificador del job de consulta. |

### GET `/jobs`
- **Descripción:** Lista todos los jobs de consulta registrados desde el último arranque de la aplicación, del más reciente al más viejo. Sirve para recuperar un `jobId` que se perdió y para ver cuáles siguen corriendo en paralelo. Los jobs viven en memoria: un reinicio de la aplicación vacía la lista.
- **Qué se requiere:** Nada.
- **Qué se obtiene:** `200 OK` con la lista de jobs. Los que siguen corriendo traen `estatus: EN_PROCESO` y `fin: null`; los terminados, `COMPLETADO` o `COMPLETADO_CON_ERRORES` con su `fin`.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| — | — | — | Ninguno. |

### GET `/excel/{jobId}`
- **Descripción:** Descarga los resultados de la consulta masiva.
- **Qué se requiere:** `jobId` en la ruta.
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de dos columnas, `TrackingNumber` y `StatusOms`. Cuando no hay un valor, la celda trae `UNKNOWN` (la respuesta no contiene `statusOms`), `null` (viene la llave con valor nulo), `PARSE_ERROR` (la respuesta no se pudo interpretar) o `ERROR_PETICION` (la llamada al servicio falló en todas las rondas). Se puede descargar aunque el job siga en proceso: trae lo que lleve hasta ese momento.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `jobId` | string (path) | Sí | Identificador del job de consulta. |

---

## SOMS — `/api/v1/soms`
Reportes y consultas sobre órdenes del sistema SOMS.

### POST `/reporte/remisiones-sin-datos`
- **Descripción:** Valida cobro (Oracle) y HRD de un conjunto de remisiones y clasifica el universo.
- **Qué se requiere:** `multipart/form-data` con un archivo `.txt` (una remisión por línea).
- **Qué se obtiene:** `200 OK` con un JSON `SOMSResponse` (`universo`, `atgCobradas`, `decommCobradas`, `noCobradas`, `hrd`, `noHrd`).

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (.txt) | Sí | Archivo de texto con una remisión por línea. |

### POST `/consultarOrdenes`
- **Descripción:** Toma una muestra aleatoria de remisiones de un Excel y las consulta contra el servicio SOAP de SOMS, espaciando las llamadas (2 s entre consultas y 10 s cada 10) para no saturar el servicio.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (remisiones en la columna A) y, opcionalmente, el parámetro `muestra`.
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) con columnas: `Remision`, `Status Datos`, `Status SOMS`, `Nodo Destinatario`, `Response`.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) con las remisiones en la columna A. |
| `muestra` | int (query) | No (default `5`) | Cantidad de remisiones a consultar al azar. Si es ≤ 0 o ≥ al total, se consultan todas. |

---

## Reproceso — `/api/v1/reproceso`
Reenvío masivo de órdenes al servicio I200 (Apigee) a partir de un Excel.

### POST `/facade/procesar`
- **Descripción:** Recibe un Excel de tres columnas (A: JSON del pedido, B: remisión, C: ItemID). Por cada fila reemplaza el `ItemID` del JSON de la columna A con el valor de la columna C y envía la orden una por una al servicio I200 de Apigee, espaciando las llamadas (1 s entre envíos y 4 s cada 10) para no saturar el servicio. Los errores se registran en la columna `Response`.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (.xlsx/.xls) de tres columnas: `A` = JSON del pedido, `B` = remisión, `C` = ItemID. Se omiten las filas cuyo contenido en la columna A no sea un objeto JSON (encabezados o filas vacías).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) con columnas: `Request Original` (JSON enviado), `TrackingNumber` (remisión de la columna B) y `Response` (respuesta completa del servicio o el error). El contenido de cada celda se trunca al límite de Excel (32 767 caracteres).

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) de tres columnas: A=JSON del pedido, B=remisión, C=ItemID. |

### POST `/node/procesar`
- **Descripción:** Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber). Por cada fila, si algún `OrderLines[].OrderLine.Store` es exactamente `"F001"` lo reemplaza por `"001"` y envía la orden una por una al servicio I200 de Apigee, espaciando las llamadas (1 s entre envíos y 4 s cada 10) para no saturar el servicio. Las órdenes que no contienen `F001` no se envían y se marcan como `No F001` en la columna `Response`. Los errores se registran también en la columna `Response`.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (.xlsx/.xls) de dos columnas: `A` = JSON del pedido, `B` = TrackingNumber. Se omiten las filas cuyo contenido en la columna A no sea un objeto JSON (encabezados o filas vacías).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) con columnas: `Request Original` (JSON enviado con el Store corregido, o el JSON original si no aplica), `TrackingNumber` (columna B) y `Response` (respuesta completa del servicio, el error, o `No F001` si la orden no se envió). El contenido de cada celda se trunca al límite de Excel (32 767 caracteres).

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) de dos columnas: A=JSON del pedido, B=TrackingNumber. |

### POST `/billto/procesar`
- **Descripción:** Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber) y rellena el nodo `PersonInfoBillTo`, que el INT200 exige, con los datos de `PersonInfoShipTo`. Es un **merge, no una copia**: se toma cada campo de `PersonInfoShipTo` (saltando las extensiones `LExtnAddressLineNINT`, `LExtnAddressLineEDIFICIO` y `LExtnATGADRID`) y solo se escribe en `PersonInfoBillTo` cuando ahí está vacío — llave ausente, `null`, `""` o solo espacios. Lo que el BillTo ya trae con valor propio se respeta. Si tras el merge queda vacío un campo obligatorio de `PersonInfoBillTo` según `int200-rules.json`, la orden **no se envía** y se reporta cuál falta; los dos obligatorios con valor por omisión (`Country` = `MX`, `AddressLine3`) se rellenan con ese valor y la orden sí se envía. Las órdenes completas se envían una por una al servicio I200 de Apigee, espaciando las llamadas (1 s entre envíos y 4 s cada 10). Se acepta el JSON con o sin la llave envolvente `Order`.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (.xlsx/.xls) de dos columnas: `A` = JSON del pedido, `B` = TrackingNumber. Se omiten las filas cuyo contenido en la columna A no sea un objeto JSON (encabezados o filas vacías).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) con columnas: `Request Original` (JSON enviado con el `PersonInfoBillTo` completo, o el JSON original si la fila no se envió), `TrackingNumber` (columna B) y `Response`, que toma una de cuatro formas:

| Columna `Response` | Significado |
|---|---|
| `Enviado \| <respuesta>` | La orden se envió y I200 respondió. Si se aplicó un valor por omisión se indica: `Enviado (default Country="MX") \| …` |
| `Falta: PersonInfoBillTo.State, …` | Quedaron obligatorios vacíos tras el merge; la orden no se envió |
| `Falta el nodo PersonInfoShipTo, no hay de donde copiar` | El JSON no trae el nodo de origen o viene sin datos; la orden no se envió |
| `"error": "…"` | La llamada a I200 falló, o el JSON de la columna A no se pudo interpretar |

El contenido de cada celda se trunca al límite de Excel (32 767 caracteres).

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) de dos columnas: A=JSON del pedido, B=TrackingNumber. |

---

## Validador — `/api/v1/validador`
Diagnóstico de órdenes contra el contrato INT200 (`INT200_SL_ATG_APV_OMS_Order_Load`) antes de enviarlas a OMS/SOMS. No envía nada a Apigee ni corrige el JSON: solo reporta.

### POST `/procesar`
- **Descripción:** Recibe un Excel de dos columnas (A: JSON del pedido, B: remisión). Por cada fila revisa el JSON contra las reglas del INT200 y separa los hallazgos en dos columnas: los **campos obligatorios que faltan** y los **errores y casos borde**. Tolera el JSON con o sin la llave envolvente `"Order"`. Las reglas viven en `src/main/resources/int200-rules.json` (campos obligatorios por bloque, longitudes, valores por default y catálogos), por lo que un cambio de catálogo no requiere recompilar. Los hallazgos usan los códigos del contrato: `CTR-002` obligatorio vacío que se rellena con el default de la definición, `CTR-004` valor fuera de catálogo, `CTR-005` longitud excedida, `CTR-006` mojibake detectado (se reporta en cualquier campo del payload, no solo en los obligatorios), `CTR-008` discrepancia conocida pendiente de confirmar con OMS, `CTR-010` campo que la definición pide vacío y llega poblado.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (.xlsx/.xls) de dos columnas: `A` = JSON del pedido, `B` = remisión. Se omiten las filas cuyo contenido en la columna A no sea un objeto JSON (encabezados o filas vacías).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) con columnas: `JSON` (el request original sin modificar), `Remisión` (columna B), `Validaciones` (rutas de los campos obligatorios ausentes, o vacíos sin default, separadas por comas — por ejemplo `PersonInfoBillTo.AddressLine2`; si falta un bloque completo se reporta la ruta del bloque) y `Errores` (hallazgos con código `CTR-*` separados por comas; si el JSON no se puede parsear, `Validaciones` queda vacía y aquí aparece `JSON inválido: ...`). El contenido de cada celda se trunca al límite de Excel (32 767 caracteres).

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) de dos columnas: A=JSON del pedido, B=remisión. |

### POST `/marketplace`
- **Descripción:** Comprueba qué se le está mandando a Entrada Única. Recibe un Excel de dos columnas (A: REQUEST de marketplace, B: tracking number) y de cada REQUEST extrae la remisión (`commercial_id`) y, por cada elemento de `offers`, el `offer_id` y el sku (el `value` del elemento de `order_line_additional_fields` cuyo `code` es `product-sap-sku-id`). **Una fila de entrada puede producir varios registros de salida:** si un REQUEST trae 3 offers, se escriben 3 filas con la misma remisión y distinto offerId/sku. Es extracción local: no consulta ningún sistema externo. El tracking number solo se usa para identificar la fila en bitácora, no aparece en el reporte.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (.xlsx/.xls) de dos columnas: `A` = REQUEST JSON, `B` = tracking number. Se omiten las filas cuyo contenido en la columna A no sea un objeto JSON (encabezados o filas vacías).
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) con columnas: `Remisión`, `OfferId`, `Sku` y `Errores`. Ninguna fila de entrada se pierde: si algo falla se emite el registro con lo que sí se pudo extraer y el motivo en `Errores` — `JSON inválido: ...` (registro único con las tres primeras columnas vacías), `El REQUEST no trae offers` (registro único conservando la remisión), `Falta commercial_id` (se repite en todos los registros de esa fila), `Falta offer_id` y `Falta product-sap-sku-id`; los motivos que coinciden en un mismo registro van separados por comas. El contenido de cada celda se trunca al límite de Excel (32 767 caracteres).

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) de dos columnas: A=REQUEST JSON, B=tracking number. |

---

## Availability — `/api/v1/availability`
Búsqueda masiva y asíncrona del stock de un SKU dentro de la orden a la que pertenece su remisión, identificada por `jobId`. Es solo lectura: encadena dos consultas GET por fila y no escribe en ningún sistema.

Por cada fila el flujo es:
1. `GET {ogcp}/order-service/v1/order/tracking-number/{remisión}` → `ctOrderId`.
2. `GET {commercetools}/{projectKey}/orders/{ctOrderId}?expand=paymentInfo.payments[*]&expand=lineItems[*].supplyChannel` con `Authorization: Bearer`.
3. Del `lineItem` cuyo `variant.sku` empata con el SKU de la columna A se toma `variant.availability.availableQuantity`.

Las URLs, el `client-id`, el `project-key` y el ritmo viven en `application.properties` bajo el prefijo `availability.*`. El *secret* del API client **no** se guarda en configuración: se envía en cada petición.

### POST `/available`
- **Descripción:** Inicia la búsqueda asíncrona de availability a partir de un Excel de dos columnas (A: SKU, B: remisión). El `access_token` de commercetools se genera al momento con el `password` recibido y se usa durante toda la corrida; si las credenciales son incorrectas la petición falla de inmediato en lugar de encolar un job que muera solo. Se consulta una fila cada 100 ms; si una respuesta trae `500 Internal Server Error` o `504 Gateway Timeout` se pausa 5 s, la fila se difiere y se sigue avanzando con las demás; al terminar se reprocesan las diferidas hasta 3 rondas.
- **Qué se requiere:** `multipart/form-data` con un archivo Excel (.xlsx/.xls) de dos columnas (`A` = SKU, `B` = remisión) y el `password` del API client de commercetools. Se omiten las filas cuya columna A no sea un SKU numérico (encabezados o filas vacías).
- **Qué se obtiene:** `202 Accepted` con el `jobId` para consultar estatus y resultados.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (Excel) | Sí | Excel (.xlsx/.xls) de dos columnas: A=SKU, B=remisión. |
| `password` | string | Sí | Secret del API client de commercetools con el que se genera el `access_token`. No se almacena ni se registra en bitácora. |

### GET `/available/estatus/{jobId}`
- **Descripción:** Consulta el estatus actual de la búsqueda: total de filas, procesadas, reprocesadas, con error de gateway y la remisión en curso.
- **Qué se requiere:** `jobId` en la ruta.
- **Qué se obtiene:** `200 OK` con el estatus del job (`EN_PROCESO`, `COMPLETADO` o `COMPLETADO_CON_ERRORES`).

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `jobId` | string (path) | Sí | Identificador del job de búsqueda. |

### GET `/available/jobs`
- **Descripción:** Lista todos los jobs de availability registrados desde el último arranque de la aplicación, del más reciente al más viejo. Sirve para recuperar un `jobId` que se perdió y para ver de un vistazo qué sigue corriendo. Los jobs viven en memoria: un reinicio de la aplicación vacía la lista.
- **Qué se requiere:** Nada.
- **Qué se obtiene:** `200 OK` con la lista de jobs, cada uno con `jobId`, `estatus`, `totalTrackings`, `procesados`, `conErrorGateway`, `reprocesados`, `trackingActual`, `inicio` y `fin`. Los que siguen corriendo traen `estatus: EN_PROCESO` y `fin: null`; los terminados, `COMPLETADO` o `COMPLETADO_CON_ERRORES` con su `fin`.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| — | — | — | Ninguno. |

### GET `/available/excel/{jobId}`
- **Descripción:** Descarga los resultados de la búsqueda.
- **Qué se requiere:** `jobId` en la ruta.
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de tres columnas: `SKU`, `Remisión` y `Stock`. `Stock` trae la cadena completa `"availableQuantity": 577,` cuando se encontró el dato; si algo falló trae `"error": "<mensaje>"` en esa misma celda (por ejemplo `"error": "SKU no encontrado en la orden"` cuando ningún `lineItem` de la orden empata con el SKU). El contenido de cada celda se trunca al límite de Excel (32 767 caracteres).

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `jobId` | string (path) | Sí | Identificador del job de búsqueda. |
