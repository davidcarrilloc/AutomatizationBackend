# ENDPOINTS — AutomatizacionBackend (VyE)

<!-- Doc para IA. Formato por endpoint: `### MÉTODO ruta-completa` + claves desc/req/res/params/impl.
params: `nombre:tipo:req|opt[=default] — nota`. Hallazgos por módulo en docs/Hallazgos_Tecnicos.md. -->

GLOBAL
- base=http://localhost:9091 · swagger=/swagger-ui/index.html · auth=Basic|FormLogin en todo.
- xlsx: `Content-Disposition: attachment`, mime `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; toda celda pasa por `ExcelService.truncarCelda` (32 767 car.).
- Excel de entrada con JSON en A: filas cuya A no es objeto JSON se omiten (encabezados/vacías).
- Jobs `jobId`: estado en memoria (`ConcurrentHashMap`), se pierde al reiniciar. estatus ∈ {EN_PROCESO, COMPLETADO, COMPLETADO_CON_ERRORES}. `/jobs` ordena más reciente→más viejo; `fin=null` si corre. jobId inexistente → 400.
- `EstatusFulfillment` = {jobId, estatus, totalTrackings, procesados, conErrorGateway, reprocesados, trackingActual, inicio, fin}.
- Ritmo I200 síncrono (`ReprocesoService`): 250 ms entre envíos, 1 s cada 25.
- "vacío" en reprocesos = llave con `null` | `""` | solo espacios. Llave ausente no se crea salvo que se diga.
- Tracking/remisión: se rellena a 10 dígitos SOLO contra OGCP (fulfillment, fulfillment-txt, availability). Contra BRIDGECORE va tal cual, bind `Types.VARCHAR`.

---

## Upload `/api/v1/upload` · UploadController

### POST /api/v1/upload/reprocesoMkpApv
desc: deposita CSV en dir APV y ejecuta script Python de reproceso.
req: multipart CSV.
res: 200, salida del script.
params: file:CSV:req

### POST /api/v1/upload/reprocesoMkpAtg
desc: igual que APV, dir ATG.
req: multipart CSV.
res: 200, salida del script.
params: file:CSV:req

### POST /api/v1/upload/cancelacionDevolucionMkpAtg
desc: cancelaciones/devoluciones MKP ATG desde Excel (cols 0,1,2,7).
req: multipart Excel.
res: 200, resultado del proceso.
params: file:Excel:req

### POST /api/v1/upload/codigosDigitales
desc: códigos digitales (ATG) de los ids en col A.
req: multipart Excel.
res: 200, lista de códigos.
params: file:Excel:req

---

## SQL `/api/v1/sql` · InitController

### POST /api/v1/sql/crearDB
desc: crea tabla de métricas TX en SQLite.
req: nada.
res: 200, resultado.
params: —

---

## TX `/api/v1/tx` · TxController

### POST /api/v1/tx/detalleTx
desc: detalle de una transacción.
req: body JSON `DetalleTxRequest`, todos obligatorios.
res: 200, detalle.
params: atgOrderId:string(body):req — `o12345678` · atgShippingGroupId:string(body):req — `sg12345678` · source:string(body):req — `LIVERPOOL`

### GET /api/v1/tx/reporte/diferencia
desc: volumen TX por hora hoy vs ayer.
req: nada.
res: 200 xlsx.
params: —

---

## OMS `/api/v1/oms` · OMSController

### POST /api/v1/oms/verificarEnOMSLiverpool
desc: verificación masiva en OMS Liverpool.
req: multipart Excel, órdenes en A.
res: 200 xlsx.
params: file:Excel:req

### POST /api/v1/oms/verificarEnOMSSuburbia
desc: idem OMS Suburbia.
req: multipart Excel, órdenes en A.
res: 200 xlsx.
params: file:Excel:req

### GET /api/v1/oms/reporte/faltantes/liverpool
desc: órdenes no enviadas a OMS Liverpool en rango.
req: query inicio, fin.
res: 200 xlsx.
params: inicio:date yyyy-MM-dd:req · fin:date yyyy-MM-dd:req

### GET /api/v1/oms/reporte/faltantes/suburbia
desc: idem Suburbia (BRIDGECORE2; celdas `is_mkp`/`tienda_cliente` vacías).
req: query inicio, fin.
res: 200 xlsx.
params: inicio:date yyyy-MM-dd:req · fin:date yyyy-MM-dd:req

---

## Fulfillment `/api/v1/fulfillment` · FulfillmentController → FulfillmentService (executor `fulfillmentExecutor` 1-2 hilos)

### POST /api/v1/fulfillment/reproceso
desc: reproceso async de fulfillment OGCP por tracking.
req: multipart Excel, ids en A.
res: 202 + jobId.
params: file:Excel:req

### GET /api/v1/fulfillment/reproceso/estatus/{jobId}
desc: estatus del job.
req: jobId.
res: 200 EstatusFulfillment.
params: jobId:string(path):req

### GET /api/v1/fulfillment/reproceso/jobs
desc: lista jobs.
req: nada.
res: 200 lista EstatusFulfillment.
params: —

### GET /api/v1/fulfillment/reproceso/excel/{jobId}
desc: descarga resultados.
req: jobId.
res: 200 xlsx.
params: jobId:string(path):req

---

## Fachada `/api/v1/fachada` · FachadaController → FachadaService

Reenvío masivo concurrente pass-through a I200 (`POST {reproceso.i200.base-url}/oms/sl/I200?origen=ecom`, header apikey). Uso: caída de sistemas externos, lotes grandes.
Config: `fachada.tamano-bloque=1000`, `fachada.factor=16`, `fachada.max-rondas=3`. W = availableProcessors × factor.

### POST /api/v1/fachada/reproceso
desc: parte Excel en bloques de `tamano-bloque`; W bloques en paralelo (hilos virtuales + `Semaphore(W)`); sin pausas; JSON enviado tal cual. Fila con CUALQUIER error → final de cola de su bloque, reintento hasta `max-rondas`, última ronda conserva error. JSON inválido se marca, no se difiere. Orden de entrada preservado.
req: multipart Excel A=JSON, B=TrackingNumber.
res: 202 + EstatusFachada {jobId, estatus, total, procesados, errores, bloques, inicio, fin}.
params: file:Excel:req

### GET /api/v1/fachada/reproceso/estatus/{jobId}
desc: estatus.
req: jobId.
res: 200 EstatusFachada.
params: jobId:string(path):req

### GET /api/v1/fachada/reproceso/jobs
desc: lista jobs.
req: nada.
res: 200 lista.
params: —

### GET /api/v1/fachada/reproceso/excel/{jobId}
desc: descarga (parcial permitida).
req: jobId.
res: 200 `Reporte_Fachada_{jobId}.xlsx` cols Request Original | TrackingNumber | Response.
params: jobId:string(path):req

---

## Fulfillment TXT `/api/v1/fulfillment-txt` · FulfillmentTxtController → StatusOmsService (executor `statusOmsExecutor`, hilo virtual por job, jobs simultáneos sin tope)

### POST /api/v1/fulfillment-txt
desc: consulta `statusOms` en OGCP fulfillment por tracking; relleno 10 dígitos; uno por uno sin pausa. Solo `ERROR_PETICION` se reintenta (al final de cola, `status-oms.max-rondas-reproceso=3`); tras última ronda se conserva.
req: multipart .txt, un tracking por línea (vacías ignoradas).
res: 202 + EstatusFulfillment.
params: file:TXT:req

### GET /api/v1/fulfillment-txt/estatus/{jobId}
desc: avance.
req: jobId.
res: 200 EstatusFulfillment | 400 si no existe.
params: jobId:string(path):req

### GET /api/v1/fulfillment-txt/jobs
desc: lista jobs.
req: nada.
res: 200 lista.
params: —

### GET /api/v1/fulfillment-txt/excel/{jobId}
desc: descarga (parcial permitida).
res: 200 xlsx TrackingNumber | StatusOms. Valores sin dato: `UNKNOWN` (llave ausente), `null` (llave nula), `PARSE_ERROR`, `ERROR_PETICION` (falló todas las rondas).
req: jobId.
params: jobId:string(path):req

---

## SOMS `/api/v1/soms` · SOMSController

### POST /api/v1/soms/reporte/remisiones-sin-datos
desc: valida cobro (Oracle) y HRD de remisiones; clasifica universo.
req: multipart .txt, una remisión por línea.
res: 200 JSON SOMSResponse {universo, atgCobradas, decommCobradas, noCobradas, hrd, noHrd}.
params: file:TXT:req

### POST /api/v1/soms/consultarOrdenes
desc: muestra aleatoria de remisiones contra SOAP SOMS; 2 s entre consultas, 10 s cada 10.
req: multipart Excel remisiones en A; muestra opcional.
res: 200 xlsx Remision | Status Datos | Status SOMS | Nodo Destinatario | Response.
params: file:Excel:req · muestra:int(query):opt=5 — ≤0 o ≥total ⇒ todas

---

## Reproceso `/api/v1/reproceso` · ReprocesoController → envío por ReprocesoService (I200 Apigee, síncrono)

Todos: req = multipart Excel (A=JSON pedido, B=TrackingNumber/remisión), res = 200 xlsx `Request Original | TrackingNumber | Response`. Request Original = JSON enviado, o original si no se envió. Tolera llave envolvente `Order` y la conserva. `/completo/procesar` aplica todo; los demás atacan una sola corrección.

### POST /api/v1/reproceso/itemid/procesar
desc: reemplaza `ItemID` del JSON por col C y envía. ItemID numérico → número, alfanumérico → texto (`ReprocesoItemIdService.nodoItemId`).
req: Excel A=JSON, B=remisión, C=ItemID.
res: 200 xlsx.
params: file:Excel:req
impl: ReprocesoItemIdService

### POST /api/v1/reproceso/itemid-auto/procesar
desc: llena Item/LinePriceInfo/EMailID desde BRIDGECORE (`consulta.detalle-sku-remision`, una consulta por remisión, `where TIP.REMISION = :remision ... order by SK.ID`, filtros `ID_CAT_ESTATUS=0`, `ID_TIPO_TX=1`).
  - #SKUs == #OrderLines → asignación posicional (SKU i → OrderLine i). SOBREESCRIBE: ItemDesc←DISPLAY_NAME, ItemID←SKU_ID (siempre TextNode), UnitPrice=ListPrice←DMS.TOTAL (`setScale(2)`). Envía.
  - descuadre (incl. JSON sin OrderLines) → no envía; reporte agrega cols `Correo`, `SKU 1..N` (`SKU_ID | DISPLAY_NAME | TOTAL`) para colocar a mano y reprocesar por `/itemid/procesar`.
  - Correo: CUSTOMER_EMAIL → toda llave exactamente `EMailID` a cualquier profundidad, solo si vacía; no crea llaves; no toca `ExtnPushEmailID`/`ExtnMKPEMail`. Si queda algún EMailID vacío → no envía.
  - Precedencia: Sin datos → descuadre → Sin correo → envío. No toca ExtnOfferID/Store/ShipNode.
  - Log: `BRIDGECORE devolvió N filas para la remisión X`.
req: Excel A=JSON, B=remisión (tal cual, sin relleno).
res: 200 `Reporte_Reproceso_ItemId_Auto.xlsx`; cols extra solo si hubo descuadre. Response ∈ {`<resp I200>`, `No enviado: BC trae N SKUs y el JSON M OrderLines`, `Sin datos en BRIDGECORE`, `Sin correo en BRIDGECORE`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoItemIdAutoService, RemisionRepository.obtenerDetalleSkuRemision, model DetalleSkuRemision

### POST /api/v1/reproceso/f001/procesar
desc: `OrderLines[].OrderLine.Store|ShipNode == "F001"` → `"001"` y envía. Sin F001 → no envía, `No F001`.
req: Excel A=JSON, B=TrackingNumber.
res: 200 xlsx; Response ∈ {`<resp>`, `No F001`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoF001Service

### POST /api/v1/reproceso/billto/procesar
desc: merge ShipTo→BillTo solo en campos vacíos del BillTo (no copia; respeta City/ZipCode propios). Excluye `LExtnAddressLineNINT`, `LExtnAddressLineEDIFICIO`, `LExtnATGADRID`. Obligatorios = bloque `PersonInfoBillTo` de `int200-rules.json`; con `valorDefault` (Country=MX, AddressLine3) se rellenan y se envía; sin default vacío → no envía. Sin PersonInfoShipTo (o vacío) → no envía. DayPhone no bloquea (Used=N).
req: Excel A=JSON, B=TrackingNumber.
res: 200 xlsx; Response ∈ {`Enviado | <resp>`, `Enviado (default Country="MX") | …`, `Falta: PersonInfoBillTo.State, …`, `Falta el nodo PersonInfoShipTo, no hay de donde copiar`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoBillToService

### POST /api/v1/reproceso/combinado/procesar
desc: F001→001 + merge BillTo en una pasada. A diferencia de `/f001`, filas sin F001 SÍ se envían (`sin cambios`). Sin ShipTo → no envía aunque BillTo esté completo. Notas en orden: F001, BillTo, `default X="…"`.
req: Excel A=JSON, B=TrackingNumber.
res: 200 `Reporte_Reproceso_Combinado.xlsx`; Response ∈ {`Enviado (F001, BillTo, default Country="MX") | <resp>`, `Enviado (sin cambios) | <resp>`, `Falta: PersonInfoBillTo.State`, `Falta el nodo PersonInfoShipTo, no hay de donde copiar`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoCombinadoService

### POST /api/v1/reproceso/email/procesar
desc: solo correo. CUSTOMER_EMAIL de BRIDGECORE (consulta de cliente TX+TIP+TX_CLIENTE, una por remisión, compartida con `/firstname`) → toda llave `EMailID` vacía a cualquier profundidad; no crea llaves; no toca `ExtnPushEmailID`/`ExtnMKPEMail`; resto del pedido intacto. EMailID vacío tras intento → no envía; pedido ya completo se envía aunque BC no traiga cliente.
req: Excel A=JSON, B=remisión (tal cual).
res: 200 xlsx; Response ∈ {`<resp I200>`, `Sin datos en BRIDGECORE`, `Sin correo en BRIDGECORE`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoEmailService

### POST /api/v1/reproceso/firstname/procesar
desc: EMailID, FirstName, MiddleName, LastName desde TX_CLIENTE (CUSTOMER_EMAIL, NOMBRE_USUARIO). Toda llave con ese nombre exacto, cualquier profundidad (incl. PersonInfoContact), solo si vacía; no crea; no toca `ExtnPushEmailID`, `LExtnCustomerLastNameP/M`. NOMBRE_USUARIO se reparte por posición: 1ª palabra→FirstName, última→LastName, medio→MiddleName; 2 palabras ⇒ MiddleName intacto y se envía. Veto si queda vacío EMailID|FirstName|LastName (mira resultado, no fuente).
req: Excel A=JSON, B=remisión (tal cual).
res: 200 `Reporte_Reproceso_FirstName.xlsx`; Response ∈ {`<resp I200>`, `Sin datos en BRIDGECORE`, `Sin datos en BRIDGECORE para: EMailID, FirstName, LastName`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoFirstNameService

### POST /api/v1/reproceso/conditionvariable2/procesar
desc: OrderLines[].OrderLine.ConditionVariable2 vacío/ausente ← PICK si TX_INFORMACION_PROCESADA.IS_CLICK_AND_COLLCT=Y, SHP si =N (vía `consulta.cliente-remision`). Crea la llave si no existe; valor propio se respeta; todo lleno ⇒ se envía tal cual. Tolera JSON con o sin llave `Order`.
req: Excel A=JSON, B=remisión (tal cual).
res: 200 `Reporte_Reproceso_ConditionVariable2.xlsx`; Response ∈ {`<resp I200>`, `Sin datos en BRIDGECORE`, `Sin datos en BRIDGECORE para: ConditionVariable2`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoConditionVariable2Service

### POST /api/v1/reproceso/greventtype/procesar
desc: corrige el error OMS `EXTN_GR_EVENT_TYPE max 24 chars`. Toda llave `ExtnGREventType` (Order.Extn, Mesa de Regalos) con >24 car. → primeros 24 sin espacios sobrantes, a cualquier profundidad, y envía. Sin la llave o ≤24 → no envía, `No ExtnGREventType > 24`. Sin BRIDGECORE. Tolera JSON con o sin llave `Order`.
req: Excel A=JSON, B=TrackingNumber.
res: 200 `Reporte_Reproceso_GrEventType.xlsx`; Response ∈ {`<resp I200>`, `No ExtnGREventType > 24`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoGrEventTypeService.recortar

### POST /api/v1/reproceso/completo/procesar
desc: 6 correcciones en orden, 2 consultas BC por remisión distinta (detalle SKU + cliente). Solo llena vacíos (precio 0 cuenta como vacío); NO pisa ItemID/precio propios (≠ itemid-auto).
  1. Store/ShipNode F001→001 — nunca frena.
  2. ItemID/ItemDesc/UnitPrice/ListPrice por posición si #SKUs==#OrderLines — si no, se salta con motivo y se envía igual.
  3. EMailID/FirstName/MiddleName/LastName desde TX_CLIENTE — frena si queda vacío EMailID|FirstName|LastName.
  4. BillTo ← ShipTo + defaults INT200 — frena si falta obligatorio sin default. Va al final para heredar paso 3.
  5. OrderLine.ConditionVariable2 vacío/ausente ← PICK (IS_CLICK_AND_COLLCT=Y) | SHP (=N); crea la llave — frena si queda vacío (flag ≠ Y/N o remisión sin BC) y se suma a `Sin datos en BRIDGECORE para: …`.
  6. ExtnGREventType >24 car. → primeros 24 (`ReprocesoGrEventTypeService.recortar`) — nunca frena.
  Sin ShipTo pero BillTo completo → SÍ envía (≠ `/billto`). Log: `BRIDGECORE devolvió N SKUs y M clientes para la remisión X`.
req: Excel A=JSON, B=remisión (tal cual).
res: 200 `Reporte_Reproceso_Completo.xlsx`; Response ∈ {`Enviado (F001, Item, Correo, BillTo, ConditionVariable2, GREventType) | <resp>`, `Enviado (sin cambios) | <resp>`, `Enviado (F001, Correo, BillTo | sin Item: BC trae 3 SKUs y el JSON 2 OrderLines) | <resp>`, `Enviado (..., default Country="MX") | <resp>`, `No enviado. Sin datos en BRIDGECORE para: EMailID, LastName`, `No enviado. Falta: PersonInfoBillTo.State`, `"error": "…"`}.
params: file:Excel:req
impl: ReprocesoCompletoService

---

## Reproceso Completo Async `/api/v1/reproceso/completo` · ReprocesoCompletoController → ReprocesoCompletoAsyncService

Misma lógica que `/reproceso/completo/procesar`, por jobId, una pasada sin pausas largas. Reintento solo de 500/504 (diferido al final, `reproceso.completo.max-rondas`); 400, JSON inválido y `No enviado…` no se reintentan. Orden de entrada preservado.

### POST /api/v1/reproceso/completo/async
desc: inicia job.
req: multipart Excel A=JSON, B=remisión.
res: 202 + EstatusFulfillment.
params: file:Excel:req

### GET /api/v1/reproceso/completo/estatus/{jobId}
desc: estatus.
req: jobId.
res: 200 EstatusFulfillment | 400.
params: jobId:string(path):req

### GET /api/v1/reproceso/completo/jobs
desc: lista jobs.
req: nada.
res: 200 lista.
params: —

### GET /api/v1/reproceso/completo/excel/{jobId}
desc: descarga; solo trae resultados al terminar.
req: jobId.
res: 200 `Reporte_Reproceso_Completo_{jobId}.xlsx`, mismas cols/valores que síncrono.
params: jobId:string(path):req

---

## Validador `/api/v1/validador` · ValidadorController → ValidadorService

Diagnóstico, no envía ni corrige.

### POST /api/v1/validador/procesar
desc: valida JSON contra `src/main/resources/int200-rules.json` (bloques obligatorios, longitudes, defaults, catálogos, campos que deben ir vacíos, discrepancias). Tolera `Order`. Regla: obligatorio → `Validaciones` si llave ausente o vacía SIN default; vacía CON default → `Errores` CTR-002. Mojibake se revisa en todo el payload (detecta, no repara). Códigos: CTR-002 default, CTR-004 catálogo, CTR-005 longitud, CTR-006 mojibake, CTR-008 discrepancia conocida, CTR-010 campo que debe ir vacío llega poblado. `References.Reference.Value` no se exige (solo Name).
req: multipart Excel A=JSON, B=remisión.
res: 200 xlsx JSON | Remisión | Validaciones (rutas separadas por coma; bloque ausente ⇒ ruta del bloque) | Errores (CTR-* o `JSON inválido: ...`).
params: file:Excel:req
ejemplo: JSON de CNC válido salvo `PersonInfoBillTo.AddressLine2` ⇒ Validaciones=`PersonInfoBillTo.AddressLine2`; Errores=3×CTR-002 (ConditionVariable1, ambos AddressLine3), CTR-010 ShipNode, CTR-008 NotificationType=LP, CTR-006 por mojibake.

### POST /api/v1/validador/marketplace
desc: extracción local de REQUEST Entrada Única MKP: remisión=`commercial_id`; por cada `offers[]`: `offer_id` y sku=`value` del primer `order_line_additional_fields[]` con `code=="product-sap-sku-id"`. 1 fila entrada → N registros (uno por offer). Col B solo para log. Sin pausas (no hay red).
req: multipart Excel A=REQUEST JSON, B=tracking.
res: 200 xlsx Remisión | OfferId | Sku | Errores. Nunca se pierde una fila: `JSON inválido: ...` (registro único vacío), `El REQUEST no trae offers` (registro único con remisión), `Falta commercial_id` (en todos los registros de la fila), `Falta offer_id`, `Falta product-sap-sku-id`; varios motivos unidos por coma.
params: file:Excel:req
impl: ValidadorService.extraerMarketplace, ExcelService.leerMarketplace/crearReporteMarketplace

---

## Consulta BC `/api/v1/consulta-bc` · ConsultaBcController → ConsultaBcService

### POST /api/v1/consulta-bc/trackingnumber
desc: TX no enviadas a OMS (`id_cat_estatus=0`, `id_tipo_tx=1`) de lista de remisiones, BRIDGECORE tx+tx_informacion_procesada. Lotes de 1000 (límite `IN` Oracle), concatenados en orden. Síncrono. Equivale a `___todo_BC_by_REMISIONremtypemkp_nooms_____.xlsx`.
req: multipart Excel 1 col A=remisiones (encabezado inocuo).
res: 200 xlsx hoja `Result`, 46 cols: REMISION, ATG_ORDER_ID, ATG_SHIP_GRP_ID, FECHA_TX_COMPRA, TOTAL_COBRADO, REM_TYPE_GR, IS_MKP, resto de TIP. Sin filas ⇒ celda `Sin resultados`.
params: file:Excel:req

### POST /api/v1/consulta-bc/trackingnumber/orden-venta
desc: igual que `/trackingnumber` pero filtra por `tip.orden_venta`. Lotes de 1000. Síncrono.
req: multipart Excel 1 col A=órdenes de venta.
res: 200 xlsx `Reporte_BC_OrdenVenta.xlsx`, mismas 46 cols que `/trackingnumber`.
params: file:Excel:req

### POST /api/v1/consulta-bc/trackingnumber/shipping-group
desc: igual que `/trackingnumber` pero filtra por `tip.atg_ship_grp_id`. Lotes de 1000. Síncrono.
req: multipart Excel 1 col A=shipping groups ATG.
res: 200 xlsx `Reporte_BC_ShippingGroup.xlsx`, mismas 46 cols que `/trackingnumber`.
params: file:Excel:req

---

## Availability `/api/v1/availability` · AvailabilityController → AvailabilityService

Flujo por fila: `GET {ogcp}/order-service/v1/order/tracking-number/{remisión 10 díg}` → ctOrderId → `GET {commercetools}/{projectKey}/orders/{ctOrderId}?expand=paymentInfo.payments[*]&expand=lineItems[*].supplyChannel` (Bearer) → lineItem con `variant.sku == SKU` → `variant.availability.availableQuantity`. Config `availability.*` (URLs, client-id, project-key, ritmo). Secret NO en config.

### POST /api/v1/availability/available
desc: token commercetools (client_credentials) se pide ANTES de devolver jobId (password malo falla en la petición). 1 fila/100 ms; 500/504 ⇒ pausa 5 s, difiere; hasta 3 rondas al final.
req: multipart Excel A=SKU, B=remisión (filas con A no numérica se omiten) + password.
res: 202 + jobId.
params: file:Excel:req · password:string:req — secret del API client, no se guarda ni se loguea

### GET /api/v1/availability/available/estatus/{jobId}
desc: estatus.
req: jobId.
res: 200 EstatusFulfillment.
params: jobId:string(path):req

### GET /api/v1/availability/available/jobs
desc: lista jobs.
req: nada.
res: 200 lista.
params: —

### GET /api/v1/availability/available/excel/{jobId}
desc: descarga.
req: jobId.
res: 200 xlsx SKU | Remisión | Stock; Stock=`"availableQuantity": 577,` o `"error": "<msg>"` (p.ej. `"error": "SKU no encontrado en la orden"`).
params: jobId:string(path):req

---

## Extracto BC a SFTP `/api/v1/extracto-bc` · ExtractoBcController → ExtractoBcService, SftpService · ExtractoBcScheduler `0 0 12 * * *`

Destino `sftp.*` (host 172.17.203.61:22, usuario LOGVAD, dir `/ecommerce_oms/`; dir vacío ⇒ home). Scheduler procesa día anterior completo (22-sep 12:00 ⇒ ventana 2026-09-21 00:00:00–23:59:59).
Archivos (1 col, sin encabezado, `\n`, sello `yyyyMMdd` = fecha de `inicio`):
- `oms_sl_<fecha>.csv` = LP SL (BRIDGECORE, tipo '1', `remision`, `consulta.extracto-bc-lp-sl`) + SBB (BRIDGECORE2, tipo '0','1', `atg_ship_grp_id`, `consulta.extracto-bc-sbb`), en ese orden, sin dedupe.
- `oms_bt_<fecha>.csv` = LP BT (BRIDGECORE, tipo '0', `orden_venta`, `consulta.extracto-bc-lp-bt`).
Filtros comunes: id_cat_estatus=0, id_tipo_tx=1, total_cobrado>0, rem_type_gr<>'2', atg_ship_grp_id/atg_order_id/col salida NOT NULL; LP añade is_click_and_collct='N'.
Entrega parcial: cada consulta en try/catch; archivo sube si ≥1 fuente ok (0 filas ⇒ archivo vacío legítimo); todas fallan ⇒ no sube (no pisa el previo).

### POST /api/v1/extracto-bc/enviar
desc: ejecución manual del scheduler para un rango.
req: query inicio, fin ISO; fin ≥ inicio.
res: 200 JSON {`oms_sl_…`: líneas | `Error: …` | `Omitido: todas las fuentes fallaron`, `oms_bt_…`: idem}.
params: inicio:datetime ISO:req — también define el sello · fin:datetime ISO:req — inclusivo

---

## Validación SL `/api/v1/validacion-sl` · ValidacionSlController → ValidacionSlService · ValidacionSlScheduler

Respuesta del consumidor en SFTP `/ecommerce_oms/out_sl/`: `existen.csv` (sí existen), `noexisten.csv`, `existen_h.csv` (histórico). 1 col, sin encabezado, nombre fijo, se sobrescriben diario.
Scheduler: 12:10 y cada 10 min hasta 13:50; guarda de fecha (sale si ya cargó hoy). Descarga los 3 en una sesión a `validacion-sl.directorio-local` (default `datos-sl/`). Endpoints de consulta leen disco local, nunca SFTP. Cada descarga upsert en SQLite `log_validacion_sl` (fecha, existen, no_existen, historico).
`archivo` ∈ {existen, noexisten, historico}; otro ⇒ 400.
Página: `/validacion-sl.html` (htmx + Chart.js desde `static/vendor/`): conteos, dona, línea 30 días, buscador, listas paginadas, descarga.

### GET /api/v1/validacion-sl/resumen
desc: último snapshot + serie.
req: nada.
res: 200 {fecha (null si nunca), conteos{existen,noexisten,historico}, serie (30 días, viejo→reciente)}.
params: —

### GET /api/v1/validacion-sl/filas
desc: bloque ≤200 filas como `<tr>` para htmx; botón "siguiente" si el bloque vino lleno; vacío ⇒ fila «Sin registros».
req: archivo, desde opcional.
res: 200 text/html.
params: archivo:string:req · desde:int:opt=0

### GET /api/v1/validacion-sl/buscar
desc: coincidencia exacta de remisión en los 3 archivos.
req: remision.
res: 200 text/html veredicto + archivos; en blanco ⇒ vacío.
params: remision:string:req

### GET /api/v1/validacion-sl/descargar
desc: CSV tal cual llegó.
req: archivo.
res: 200 text/csv attachment | 400 si no descargado.
params: archivo:string:req

### POST /api/v1/validacion-sl/refrescar
desc: fuerza lectura SFTP.
req: nada.
res: 200 cuerpo de `/resumen` actualizado | 500 si falta algún archivo (snapshot previo intacto).
params: —

---

## Reenvío fulfillment `/api/v1/reenvio` · ReenvioController → ReenvioService, ReenvioRepository

Reenvío masivo al fulfillment (`FulfillmentService.consultarFulfillment`, OGCP `/order-service/v1/order/fulFillment`) de todas las cobradas, día por día (00:00:00 ≤ FECHA_TX_COMPRA < día+1), desde ayer hacia atrás hasta `reenvio.fecha-limite` (2026-06-15) inclusive, saltando días en bitácora. Una sola corrida; executor propio `reenvioExecutor` (hilo virtual), no comparte cola con `/fulfillment/reproceso`.
Origen (`consulta.reenvio-dia`, `bridgeCoreDataSource`, UNION ALL): LP BT (BRIDGECORE, tipo '0') → `orden_venta` `LP_BT`; LP SL (tipo '1') → `remision` `LP_SL`; SBB (BRIDGECORE2, tipo '0','1') → `NVL(tracking_number, atg_ship_grp_id)` `SBB_TRACKING`|`SBB_SG`. Filtros: id_cat_estatus=0, id_tipo_tx=1, total_cobrado>0, atg_ship_grp_id/atg_order_id NOT NULL. Sin filtro C&C ni rem_type_gr. Valores nulos/vacíos fuera, dedupe por (remisión, origen). Sin relleno a 10 dígitos.
Ritmo: secuencial sin pausa; 500/504 ⇒ pausa 3 s, difiere al final del día; hasta 3 rondas, la última conserva el error.
Categoría: `ERROR` si response trae `"error"` o JSON no parsea; si no `FAILURE` si statusMkp|statusOms=FAILURE; si no `SUCCESS` si algún status SUCCESS; si no `OTRO`.
SQLite: `reenvio_bitacora` (fecha PK yyyy-MM-dd, total, success, failure, error, otro, inicio, fin) se escribe al cerrar el día; `reenvio_resultado` (fecha, remision, origen, categoria, response, json) se escribe por remisión; al empezar un día se borran sus resultados (día interrumpido se repite completo).
`EstatusReenvio` = {estatus, fechaActual, diasProcesados, diasTotales, totalDia, procesadasDia, remisionActual, success, failure, error, otro, inicio, fin, mensaje}; estatus ∈ {SIN_INICIAR, EN_PROCESO, DETENIENDO, DETENIDO, COMPLETADO, ERROR}. En memoria: reinicio de la app ⇒ SIN_INICIAR.

### POST /api/v1/reenvio/ejecutar
desc: play/pausa. Sin corrida ⇒ arranca con los días pendientes (ayer→límite menos bitácora). Con corrida ⇒ `DETENIENDO`, termina la remisión en curso y queda `DETENIDO`. Sin pendientes ⇒ `COMPLETADO`.
req: nada.
res: 202 EstatusReenvio.
params: —

### POST /api/v1/reenvio/reiniciar
desc: detiene la corrida viva y espera a que termine (≤ una llamada o 3 s de pausa); luego arranca desde ayer saltando bitácora.
req: nada.
res: 202 EstatusReenvio.
params: —

### GET /api/v1/reenvio/estatus
desc: avance de la corrida en memoria.
req: nada.
res: 200 EstatusReenvio.
params: —

### POST /api/v1/reenvio/limpiar
desc: detiene la corrida viva (espera a que termine) y borra lo indicado. SUCCESS|FAILURE|ERROR|OTRO ⇒ borra esos `reenvio_resultado` (salen del Excel y conteos); DIAS ⇒ vacía `reenvio_bitacora` (días se vuelven a recorrer); TODO ⇒ todo + estatus `SIN_INICIAR`. Al recorrer un día se saltan remisiones con resultado ⇒ DIAS+FAILURE reenvía solo las FAILURE. Irreversible.
req: query `tipos` (uno o varios, case-insensitive).
res: 200 EstatusReenvio con `mensaje` = conteo borrado | 400 si vacío o tipo inválido.
params: tipos:List<String>:req — SUCCESS|FAILURE|ERROR|OTRO|DIAS|TODO
impl: ReenvioService.limpiar/validarTipos, ReenvioRepository.borrarResultados/borrarBitacora

### GET /api/v1/reenvio/bitacora
desc: días procesados, fecha desc.
req: nada.
res: 200 lista {fecha, total, success, failure, error, otro, inicio, fin}.
params: —

### GET /api/v1/reenvio/excel
desc: descarga resultados del rango (SXSSF, streaming).
req: query opcional fechaInicio, fechaFin ISO date.
res: 200 xlsx Fecha | Remisión | Origen | Categoría | Response | JSON; 400 si >1 048 575 filas o fecha mal formada.
params: fechaInicio:date yyyy-MM-dd:opt — inclusivo; sin él, desde el inicio · fechaFin:date yyyy-MM-dd:opt — inclusivo; sin él, hasta el final

---

## Memoria `/api/v1/memoria` · MemoriaController → MemoriaService

### POST /api/v1/memoria/liberar
desc: vacía `estatusPorJob`/`resultadosPorJob` de FulfillmentService, ReprocesoCompletoAsyncService, AvailabilityService, FachadaService, StatusOmsService; Reenvío ⇒ detiene sin `join`, `SIN_INICIAR`, `/ejecutar` puede arrancar. Jobs vivos lanzan `CancellationException` en su siguiente iteración; encolados salen al arrancar. SQLite intacto. Llamada HTTP colgada sigue ocupando su hilo.
req: nada.
res: 200 {fulfillment, reprocesoCompleto, availability, fachada, fulfillmentTxt: int jobs liberados, reenvio: EstatusReenvio}.
params: —
impl: `liberarMemoria()` + `verificarVigente(jobId)` en cada servicio; ReenvioService.esVigente (hiloCorrida)
