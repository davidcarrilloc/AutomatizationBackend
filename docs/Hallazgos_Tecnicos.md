# HALLAZGOS TÉCNICOS

<!-- Doc para IA. Trampas ya resueltas y decisiones no deducibles del código. Leer antes de tocar el módulo.
Formato: `- [tema] hecho → consecuencia/regla`. Transversales primero, luego por módulo. Endpoints: docs/Documentacion_Endpoints.md. -->

## TRANSVERSAL

### Oracle / queries.properties
- [comentarios] líneas con `\` se unen en una ⇒ `--` comenta el resto de la query. Nunca `--` dentro de una query.
- [@Value] `@Value` a clave inexistente compila pero tumba el `ApplicationContext`. Query y `@Value` van juntos.
- [intervalos] `fecha_fin_tx - fecha_ini_tx` (TIMESTAMP) = INTERVAL DAY TO SECOND. `BigDecimal` ⇒ ORA-17004. Mapear a `String`.
- [BRIDGECORE2] sin columnas `is_mkp` ni `tienda_cliente`; query SBB las omite, encabezado del Excel igual LP/SBB.
- [datasource] `bridgeCoreDataSource` (USR_VENTA_ENTREGA@APPSPRO) ve BRIDGECORE y BRIDGECORE2; no crear otro.
- [OMS faltantes] `customer_email` y el `INNER JOIN tx_cliente tc` se quitaron; alias `tc` ya no existe.
- [esquema] SELECT contra esquema/filtro equivocado no falla, devuelve vacío ⇒ se ve como "Sin datos". Loguear conteo por consulta.
- [IN] Oracle admite ≤1000 elementos en `IN` ⇒ lotes de 1000 (`ConsultaBcService`).
- [remisión] relleno a 10 dígitos aplica SOLO a OGCP. Contra BRIDGECORE: tal cual, bind `Types.VARCHAR`. Bug histórico: mapa agrupado por `TIP.REMISION` y búsqueda por remisión rellenada ⇒ todas las <10 dígitos daban "Sin datos". Usar la misma expresión para llave de armado y de búsqueda.
- [DMS.TOTAL] importe de línea (no conteo). `BigDecimal` + `setScale(2)` (Oracle da `1519.2`, INT200 espera `1519.20`). Con CANTIDAD>1 infla UnitPrice; alternativa `SK.PRECIO_VENTA`.

### Spring
- [@Async] `this.metodo()` salta el proxy ⇒ síncrono. Auto-inyectar `@Lazy Self self` y llamar `self.procesarJob()`.
- [scheduling] sin `spring.task.scheduling.pool.size` todos los `@Scheduled` comparten 1 hilo; `TxScheduler` (30/60 s) se congela tras una tarea lenta. Valor actual 4.
- [executors] `fulfillmentExecutor` 1-2 hilos (solo `/fulfillment/reproceso`). `statusOmsExecutor` hilos virtuales (fulfillment-txt, fachada). No compartir: un job esperaría detrás de otro.
- [singleton] servicios son singleton + virtual threads ⇒ estado por petición (notas, filas enviadas, cols SKU) en variables LOCALES, nunca campos. `ReprocesoNodeRow` es `@Data` ⇒ usar `IdentityHashMap`, no `HashMap`.
- [jobs] estado en `ConcurrentHashMap` en memoria; reinicio los borra.
- [errores scheduler] schedulers antiguos no capturan; Spring se traga la excepción. Nuevos: try/catch y error en resumen.

### WebClient / APIs externas
- [codec] default 256 KB ⇒ `DataBufferLimitException` con órdenes commercetools expandidas. `maxInMemorySize(10 MB)` local al cliente.
- [pool Netty] default ~16 conexiones topa el paralelismo I/O. Fachada: `ConnectionProvider` propio `maxConnections=W`, `pendingAcquireMaxCount(-1)`; por eso no usa el WebClient compartido.
- [secrets] secret commercetools no va en properties: parámetro `password` del POST, se cambia por token al iniciar el job, solo en memoria, no se loguea. `client-id` sí está en config (no es secreto). Token OAuth: `POST https://auth.us-central1.gcp.commercetools.com/oauth/token?grant_type=client_credentials`, Basic client-id:secret, `expires_in` 172800.
- [token temprano] pedir token antes de devolver jobId ⇒ credenciales malas fallan en la petición.
- [límite real] I200/gateway es el cuello, no CPU. Bajar `fachada.factor`/`tamano-bloque` si aparecen 500/504.

### SFTP
- [lib] `com.github.mwiede:jsch:2.28.7` (paquete `com.jcraft.jsch`). `com.jcraft:jsch` abandonado (no negocia algoritmos actuales). No existe `0.2.27`: salto 0.2.x → 2.28.x.
- [destino] `https://transferencias.liverpool.com.mx:10443/` es portal Sterling, NO el SFTP. Real: `sftp LOGVAD@172.17.203.61:22`.
- [sesión] abrir/cerrar por operación; bajada de out_sl trae los 3 archivos en una sesión. `StrictHostKeyChecking=no` (sin known_hosts).
- [reintentos] cron cada 10 min + guarda `LocalDate.now()` vs última carga exitosa; sin contador.
- [horarios] ExtractoBc 12:00 y ValidacionSl 12:10 amarrados: mover uno obliga a mover el otro.

### Frontend
- [plantillas] sin motor; fragmentos `<tr>` con `StringBuilder` + `produces=TEXT_HTML_VALUE`.
- [XSS] CSV de out_sl lo escribe un tercero ⇒ `escapar()` (`& < > "`) antes de HTML. `archivo` se resuelve por mapa de 3 claves, nunca a ruta; otro ⇒ 400.
- [vendor] servidor sin internet ⇒ htmx y Chart.js versionados en `static/vendor/`, no CDN.

### Excel (POI)
- [celda] límite 32 767 car.; excederlo tumba el reporte. Todo JSON/XML largo por `ExcelService.truncarCelda`.
- [cero inicial] Excel convierte `0399010722` → `399010722`. Contra OGCP se rellena; contra BC hay que corregir el archivo.

### Verificación
- Arrancar la app abre 4 datasources Oracle inaccesibles desde dev ⇒ Swagger casi nunca es opción. Probar con harness de asserts (skill `verificacion-local`), build con JDK 23.
- Pendiente E2E en servidor para: fulfillment-txt (2 jobs simultáneos), fachada (I200 real), availability (requiere secret), billto, combinado, itemid-auto (remisión 130116287: log `BRIDGECORE devolvió N filas…`; 0 filas ⇒ problema de consulta, no de código), validador, marketplace, extracto-bc/validacion-sl (SFTP real, permisos LOGVAD en `/ecommerce_oms/` y `/out_sl/`, TxScheduler sigue latiendo durante subida).

## POR MÓDULO

### Fulfillment TXT (StatusOmsService)
- [origen] reemplazó clase `OrderFulfillmentApp` (HttpClient + parseo `indexOf`). Primera versión síncrona con 50 llamadas concurrentes castigaba al gateway ⇒ concurrencia movida a "N jobs, cada uno secuencial".
- [UNKNOWN vs null] Jackson no distingue llave ausente de nula; `json.contains("\"statusOms\"")` previo al parseo preserva `UNKNOWN`. No quitarlo.
- [reintentos] solo `ERROR_PETICION` (`esErrorGateway`); UNKNOWN/null/PARSE_ERROR son respuestas reales. Tope de rondas obligatorio: sin pausas, gateway caído = bucle infinito; última ronda conserva error para que la fila no desaparezca.
- [duplicación] copia `llamarFulfillment` y `rellenarDiezDigitos` de FulfillmentService a propósito. Extraer cliente si llega un 3er consumidor.
- [txt] `ExcelService.leerLineasDeTxt`/`esArchivoNoTxt` públicos; `SOMSService` tiene copia privada pendiente de limpiar.

### Fachada (FachadaService)
- [concurrencia] bloques en hilos virtuales acotados por `Semaphore(W)`; 1M filas no dispara 1000 hilos. Slots por índice de bloque ⇒ orden de entrada.
- [diferido] ante CUALQUIER error (≠ fulfillment, solo 500/504). Error persistente (400) = 1+max-rondas envíos.

### Reproceso BillTo / Combinado / Completo
- [merge] no es copia: el ejemplo del spec traía City/ZipCode propios del BillTo que se respetan. Copiar pisaría direcciones reales.
- [obligatorios] salen de `int200-rules.json` bloque `PersonInfoBillTo` (misma fuente que ValidadorService). No codificar listas en Java.
- [DayPhone] M en INT200 pero Used=N ⇒ se copia, no bloquea.
- [marca Enviado] se agrega después del envío: `ReprocesoService` es genérico; resultados se zipean con filas por índice.
- [duplicación] merge BillTo (~40 líneas) duplicado en ReprocesoBillToService y ReprocesoCombinadoService: cambio de regla ⇒ editar ambos. `ReprocesoBillToService.estaVacio` es `static` para reuso.
- [rules loader] ValidadorService, ReprocesoBillToService, ReprocesoCombinadoService cargan `int200-rules.json` cada uno (6 líneas). Candidato a `@Bean Int200Rules`.
- [combinado] envía filas sin F001 ⇒ archivo de 200 filas manda 200 (minutos por ritmo). Para filtrar usar `/f001`. Para archivos grandes usar `/completo/async`.

### Reproceso ItemID auto (ReprocesoItemIdAutoService)
- [emparejamiento] query no trae número de línea; asignación posicional por `order by SK.ID` (decisión del usuario). Descuadre ⇒ no envía, SKUs a columnas para colocación manual.
- [conteo] comparar #SKUs vs #OrderLines, no `filas > 1` (filas duplicadas por join a TX_DETALLE_MONTO_SKU bloqueaban órdenes válidas). JSON sin OrderLines = descuadre (antes se enviaba intacto como éxito).
- [ItemID] siempre `TextNode` aquí; `/itemid` conserva regla numérico/texto.
- [correo] búsqueda por nombre de llave `EMailID` recursiva, solo llaves existentes, solo si vacía. Veto mira el resultado (EMailID vacío), no la fuente.
- [ExtnOfferID] fuera: el SQL no trae offer id.
- [reporte] `crearReporteReproceso` con ancho dinámico: sin `skus`/`correo` sale idéntico (3 cols), otros endpoints no cambian.
- [remisión vacía] se omite sin consultar.

### Validador INT200 (ValidadorService)
- [obligatoriedad] hoja `Input Message` A5:Q1409 de `INT200_SL_ATG_APV_OMS_Order_Load.xlsx`: obligatorio ⇔ `Used=Y` ∧ `O/M=M` en el campo Y en su sección. Solo `O/M` da falsos positivos: PersonInfoContact (sección O) fuera; hijos M de `OrderLine.Extn` (sección O) fuera; `RetailPrice` y `PersonInfo*.DayPhone` (Used=N) fuera; PersonInfoShipTo duplicado (r683 Used=N, r752 Used=Y) vale r752; `"O M"` = opcional; BillToID `"M\nO"` = obligatorio.
- [falta ≠ vacío] ausente o vacío sin default ⇒ Validaciones; vacío con default ⇒ CTR-002.
- [Reference.Value] M en hoja pero vacío legítimo (SellerName, ExtnComments, ExtnEmployeeNumber); solo se exige Name.
- [diseño] no se implementó `OrderContractProcessor`/`Finding`/`Severity` de CONTRATO_VALIDADOR.md §6.4 (dimensionado para normalizador de fachada).
- Contrato fuente, catálogos y puntos abiertos con OMS: `CONTRATO_VALIDADOR.md`.

### Validador Marketplace
- [pausa] spec pedía 100 ms por error; descartada (sin red, solo `readTree`).
- [col B] tracking no va al reporte; solo log cuando el REQUEST no parsea.
- [sku] no está en raíz del offer: recorrer `order_line_additional_fields` por `code`, primer match.
- [errores] `commercial_id` faltante se repite en todos los registros de la fila; offer_id/sku solo en su registro.

### Availability (AvailabilityService)
- [spec] `FulfillmentController.enviarFulfillment` no existe; el patrón copiado es `reprocesarFulfillment` (async).
- [URL] se quitó el `where=custom(fields(orderNumber=…))` del spec: no derivable y no aplica a GET por id. `expand` se conservan.
- [modelo] reusa `EstatusFulfillment` (remisión = tracking).

### Extracto BC (ExtractoBcService)
- [alcance] 3 consultas → 2 archivos `oms_sl_`/`oms_bt_` (pedido del consumidor OMS). Horario pasó de cada hora a diario 12:00 sobre día anterior: con sello `yyyyMMdd` las 24 corridas horarias se pisaban.
- [CSV] sin modelo/RowMapper/librería: `queryForList(..., String.class)` + `String.join("\n")`. No traer `tip.*` (46 cols para usar 1).
- [NULL] `IS NOT NULL` sobre la columna exportada (spec no lo tenía) evita líneas en blanco.
