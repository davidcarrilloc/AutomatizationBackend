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

### GET `/reproceso/excel/{jobId}`
- **Descripción:** Descarga los resultados del reproceso.
- **Qué se requiere:** `jobId` en la ruta.
- **Qué se obtiene:** `200 OK` con un archivo `.xlsx` (descarga) de resultados.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `jobId` | string (path) | Sí | Identificador del job de reproceso. |

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

## Correos — `/api/v1/correos`
Procesamiento asíncrono de transacciones por correo identificado por `jobId`.

### POST `/transaccionesPorCorreo`
- **Descripción:** Inicia un procesamiento asíncrono de transacciones a partir de correos segmentados.
- **Qué se requiere:** `multipart/form-data` con un archivo `.zip`.
- **Qué se obtiene:** `202 Accepted` con el `jobId` para consultar el estatus.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo (.zip) | Sí | Archivo ZIP con los correos segmentados. |

### GET `/transaccionesPorCorreo/estatus/{jobId}`
- **Descripción:** Consulta el estatus actual del procesamiento.
- **Qué se requiere:** `jobId` en la ruta.
- **Qué se obtiene:** `200 OK` con el estatus del job.

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `jobId` | string (path) | Sí | Identificador del job de procesamiento. |

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
