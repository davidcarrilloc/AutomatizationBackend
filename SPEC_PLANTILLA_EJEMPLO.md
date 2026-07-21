# SPEC: Reproceso masivo de Fulfillment

> Estado: **Implementado**
>
> Ejemplo de uso de `SPEC_PLANTILLA.md`, llenado con un módulo real del proyecto.
> Compara el Brief (lo que se pidió) contra los Hallazgos (lo que se descubrió al construirlo).

Necesito que actualices una automatización con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y cláusulas necesarias establecidas en CLAUDE.md

## Brief

**Por qué:** el reproceso de fulfillment se hacía tracking por tracking a mano; con lotes de cientos es inviable y el gateway se cae a media corrida.

**Entrada:** Excel de una sola columna. Columna A = trackingnumbers, uno por fila.

**Origen:** endpoint ya existente `POST https://ogcp-apigke-site-d.liverpool.com.mx/order-service/v1/order/fulFillment`, el mismo que usa `enviarFulfillment`.

**Regla:** por cada trackingnumber se consulta el fulfillment y se guarda su respuesta.
- Filas que NO aplican: no hay filtro, se procesan todas.

**Salida:** `.xlsx` descargable con TrackingNumber, Response y JSON completo.
- Errores: en la columna Response, como `"error": "<mensaje>"`.
- Trunca lo que pase el límite de Excel (32 767 car.)

**Ritmo:** si la respuesta trae `500 Internal Server Error`, pausa 10 s y sigue avanzando con los demás. Al final, reprocesa todas las que fallaron por gateway.

**Job asíncrono:** sí. Devuelve un `jobId` y déjame consultar el avance cuando quiera, más un endpoint para descargar el Excel de las que ya se procesaron.

**Reutiliza:** `FulfillmentController.enviarFulfillment` y su servicio.

## Cierre

**Componentes:**
- `controller/FulfillmentController.java` — `POST /reproceso` (202 + jobId), `GET /reproceso/estatus/{jobId}`, `GET /reproceso/excel/{jobId}`
- `service/FulfillmentService.java` — `iniciarReproceso`, `procesarJob` (`@Async`), `obtenerEstatus`, `obtenerResultados`
- `model/EstatusFulfillment.java` — avance del job (total, procesados, conErrorGateway, reprocesados, trackingActual, inicio, fin)
- `model/FulfillmentResult.java` — fila del reporte
- `service/ExcelService.java` — `crearReporteFulfillment`

**Hallazgos:**
1. **`@Async` no aplica en llamadas internas.** `this.procesarJob(...)` se salta el proxy de Spring y corre síncrono. Se resolvió auto-inyectando el servicio: `@Lazy FulfillmentService self` y llamando `self.procesarJob(...)`. → también en `docs/Hallazgos_Tecnicos.md`
2. **Los trackings vienen con ceros a la izquierda perdidos.** Excel se come el `0` inicial, así que se rellenan a 10 dígitos (`rellenarDiezDigitos`). Esto no estaba en el brief y es la causa más común de "no encuentra la orden".
3. **El brief sólo mencionaba el 500, pero el gateway también devuelve 504.** La detección cubre `500 Internal Server Error` y `504 Gateway Timeout`, por coincidencia de string sobre la respuesta.
4. **"Vuélvelas a reprocesar" no decía cuántas veces.** Se fijó en 3 rondas (`MAX_RONDAS_REPROCESO`); en la última ronda el resultado con error se conserva en lugar de descartarse.
5. **"Pausa y sigue avanzando" se interpretó como diferir, no como reintentar en el momento:** el tracking que falla se pausa 10 s y se manda al final de la cola.
6. **El estado vive en memoria** (`ConcurrentHashMap` por jobId). Los jobs se pierden al reiniciar la app. Si eso llega a importar, hay que persistirlos en SQLite.

**Verificación:** `mvn clean package -DskipTests` con JDK 23. En Swagger: subir un Excel de ~5 trackings a `POST /api/v1/fulfillment/reproceso`, consultar `GET /reproceso/estatus/{jobId}` mientras corre y confirmar que `procesados` avanza, y descargar el `.xlsx` con `GET /reproceso/excel/{jobId}`.
