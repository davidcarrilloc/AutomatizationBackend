# SPEC: Busqueda masiva de availability

> Estado: **Implementado**

Necesito que actualices una automatización con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y cláusulas necesarias establecidas en CLAUDE.md

## Brief

**Por qué:** Se va a buscar dado un SKU su nodo availability.availableQuantity y ese resultado se va a guardar en una columna del excel

**Entrada:** Excel de dos columnas. Columna A = SKU, columna B = Remision, uno por fila.

**Origen:** Vas a crear dos enpoints nuevos:
- https://ogcp-apigke-site-d.liverpool.com.mx/order-service/v1/order/tracking-number/8320116236
Este primer endpoint va a buscar la Remision
Va a devolver    "ctOrderId": "3ae2a2cb-703f-48b1-b2b5-b99b6f6c9eb8",
- https://api.us-central1.gcp.commercetools.com/liverpool-prod-01/orders/{{ctOrderId}}?expand=paymentInfo.payments[*]&expand=lineItems[*].supplyChannel&where=custom(fields(orderNumber="7a8e6d42-76d7-4b21-a790-240504a06acf"))
Este segundo endpoint se le va a pasar el ctOrderId que va a remplazar a la cadena {{ctOrderId}}

Va a regresar lo siguiente:
"variant": {
    "availability": {
    "isOnStock": true,
    "availableQuantity": 577,

Resultado esperado: "availableQuantity": 577,

**Regla:** por cada trackingnumber se consultan los endpoints de la forma en la cual veas mejor, y mas optimo
- Filas que NO aplican: no hay filtro, se procesan todas.

**Salida:** `.xlsx` descargable con SKU, Remision y Stock completo.
El Stock debe ser: "availableQuantity": 577, la cadena completa
- Errores: en la columna Response, como `"error": "<mensaje>"`.
- Trunca lo que pase el límite de Excel (32 767 car.)

**Ritmo:** Sin ritmo designado, a lo mucho 100 ms para no fastidiar el endpoint o lo que creas necesario, si la respuesta trae `500 Internal Server Error`, pausa 5 s y sigue avanzando con los demás. Al final, reprocesa todas las que fallaron por gateway.

**Reutiliza:** `FulfillmentController.enviarFulfillment` y su servicio.

## Cierre

### Componentes

| Archivo | Qué hace |
|---|---|
| `controller/AvailabilityController.java` | `/api/v1/availability` con los 3 endpoints: `POST /available` (202 + `jobId`), `GET /available/estatus/{jobId}`, `GET /available/excel/{jobId}`. |
| `service/AvailabilityService.java` | Token, encadenado de las dos consultas, extracción del stock, ritmo y reproceso de diferidas. |
| `model/AvailabilityRow.java` / `AvailabilityResult.java` | Fila de entrada (`sku`, `remision`) y de salida (`sku`, `remision`, `stock`). |
| `payload/response/TokenResponse.java` | `access_token` / `expires_in` de commercetools. |
| `service/ExcelService.java` | `leerAvailability` y `crearReporteAvailability` (hoja `Availability`, 3 columnas). |
| `application.properties` | Prefijo `availability.*`: URLs, `client-id`, `project-key` y ritmo. |

Se reutilizó sin tocar: `EstatusFulfillment` (la remisión *es* un tracking number, los campos calzan),
`AsyncConfig.fulfillmentExecutor`, `ExcelService.esArchivoNoExcel` / `leerCelda` / `truncarCelda`,
y el mapeo de `IllegalArgumentException` → 400 que ya tiene `ControllerAdvice`.

### Hallazgos

1. **`FulfillmentController.enviarFulfillment` no existe.** El método real es `reprocesarFulfillment`
   y su flujo es asíncrono con `jobId` + estatus + descarga. Ese es el patrón que se copió, en un
   controller nuevo; `FulfillmentController` no se modificó.

2. **Se quitó el `where` de la URL de commercetools.** El `orderNumber` del ejemplo
   (`7a8e6d42-…`) no se deriva de la entrada (SKU + remisión) ni del `ctOrderId`, y un `where`
   sobre un GET por id no aplica. Los dos `expand` sí se conservaron tal cual.

3. **Hubo que subir el límite de memoria del codec.** Con los `expand`, la orden supera los 256 KB
   que WebClient permite por defecto y la consulta reventaría con `DataBufferLimitException`. El
   cliente de commercetools se construye con `maxInMemorySize(10 MB)`; es un ajuste local, no se
   tocó ninguna configuración global.

4. **El *secret* del API client no está en `application.properties`.** Viaja como parámetro del
   `POST`, se cambia por un `access_token` una sola vez al arrancar el job y vive únicamente en
   memoria durante la corrida. No se guarda ni se escribe en bitácora. El `client-id`
   (`CvYLMRtNr5ab76bNBn5OZt4S`) sí quedó en configuración porque no es secreto.

5. **El token se pide antes de devolver el `jobId`.** Así una contraseña incorrecta falla en la
   misma petición, en vez de encolar un job que muere en silencio.

6. **La remisión se rellena a 10 dígitos** con el mismo criterio de
   `FulfillmentService.rellenarDiezDigitos`, porque pega contra el mismo servicio de OGCP.

### Verificación

- `mvn clean package -DskipTests` con JDK 23 → **BUILD SUCCESS**.
- Comprobación de `extraerStock` con una orden de tres `lineItems` de SKUs distintos. Los 6 casos
  pasan: empata el segundo `lineItem` (no el primero), empata el primero, SKU inexistente →
  `"error": "SKU no encontrado en la orden"`, SKU sin nodo `availability`, JSON inválido y orden
  sin `lineItems`.
- **Falta la prueba de extremo a extremo por Swagger:** requiere el *password* del API client de
  commercetools (que por diseño no está en el repo) y arrancar la app, que a su vez abre los cuatro
  datasources Oracle, no alcanzables desde este equipo.

---

- Este es un ejemplo completo:
https://ogcp-apigke-site-d.liverpool.com.mx/order-service/v1/order/tracking-number/8320116236
Response:
{
"ctOrderId": "3ae2a2cb-703f-48b1-b2b5-b99b6f6c9eb8",

- Luego:
https://api.us-central1.gcp.commercetools.com/liverpool-prod-01/orders/3ae2a2cb-703f-48b1-b2b5-b99b6f6c9eb8?expand=paymentInfo.payments[*]&expand=lineItems[*].supplyChannel&where=custom(fields(orderNumber="7a8e6d42-76d7-4b21-a790-240504a06acf"))Response:
{
"type": "Order",
"id": "3ae2a2cb-703f-48b1-b2b5-b99b6f6c9eb8",
"version": 3,
"versionModifiedAt": "2026-08-02T14:54:49.245Z",
"lastMessageSequenceNumber": 1,
"createdAt": "2026-08-02T14:54:45.661Z",
"lastModifiedAt": "2026-08-02T14:54:49.245Z",
"lastModifiedBy": {
"clientId": "jlcG-TP9qd3yhKbcbYUXE2T4",
"isPlatformClient": false
},
"createdBy": {
"clientId": "D9goFvU_Fw-hsN5bVrXsFZ_R",
"isPlatformClient": false
},
"anonymousId": "buynow-10056850513",
"totalPrice": {
"type": "centPrecision",
"currencyCode": "MXN",
"centAmount": 64950,
"fractionDigits": 2
},
"taxedPrice": {
"totalNet": {
"type": "centPrecision",
"currencyCode": "MXN",
"centAmount": 64950,
"fractionDigits": 2
},
"totalGross": {
"type": "centPrecision",
"currencyCode": "MXN",
"centAmount": 64950,
"fractionDigits": 2
},
"taxPortions": [
{
"rate": 0.0,
"amount": {
"type": "centPrecision",
"currencyCode": "MXN",
"centAmount": 0,
"fractionDigits": 2
},
"name": "Zero tax"
}
],
"totalTax": {
"type": "centPrecision",
"currencyCode": "MXN",
"centAmount": 0,
"fractionDigits": 2
}
},
"country": "MX",
"orderState": "Open",
"syncInfo": [],
"returnInfo": [],
"priceRoundingMode": "HalfEven",
"taxMode": "Platform",
"inventoryMode": "None",
"taxRoundingMode": "HalfEven",
"taxCalculationMode": "LineItemLevel",
"origin": "Customer",
"shippingMode": "Single",
"shippingAddress": {
"streetName": "Av. Periferico",
"streetNumber": "3278",
"postalCode": "09910",
"city": "Iztapalapa",
"state": "MEXICO",
"country": "MX",
"phone": "5591284600",
"additionalAddressInfo": "{\"settlement\":\"Iztapalapa\",\"contactFullName\":null,\"contactPhoneNumber\":null,\"contactEmail\":null,\"userId\":10056850513,\"name\":\"Monserrat\",\"userLastName\":\"Saldaña\",\"secondLastName\":\"Garcés \",\"loginEmail\":\"monssesg@gmail.com\",\"notificationEmail\":\"monssesg@gmail.com\",\"gender\":\"F\",\"isActive\":true,\"stateId\":\"15\",\"taxCode\":null,\"storeName\":\"Liverpool Parque Las Antenas\",\"latitude\":19.313263,\"longitude\":-99.0768481,\"moduleCnC\":\"Planta baja, a un costado de la salida a Periférico. | Acceso por entrada principal del estacionamiento Av. Canal de Garay. Cajón K4 frente a Liverpool Horario: Lun - Dom 11:00 a 21:00 hrs\",\"supportsDriveThru\":true,\"dateOfBirth\":\"05/11/1996\"}",
"externalId": "456",
"key": "shippingAddress",
"custom": {
"type": {
"typeId": "type",
"id": "c864f047-b1c2-4e52-9ee8-f22b757b03fa"
},
"fields": {
"deliveryType": "IN_STORE"
}
}
},
"shipping": [],
"discountTypeCombination": {
"type": "Stacking"
},
"lineItems": [
{
"id": "a01af209-fcbd-440e-9152-db1486166baa",
"productId": "774ff83b-dfef-44b5-acaa-b466d4e8b03a",
"productKey": "1193114901",
"productType": {
"typeId": "product-type",
"id": "3a6fb86a-0b13-4a8e-bb11-fc3cadf43457"
},
"productSlug": {
"es-MX": "1193114901"
},
"name": {
"es-MX": "Sudadera con capucha y bolsa Puma Ess Script para mujer"
},
"variant": {
"id": 3,
"sku": "1193115744",
"key": "1193115744",
"prices": [],
"images": [],
"attributes": [
{
"name": "isImportationProduct",
"value": false
},
{
"name": "comfortService",
"value": ""
},
{
"name": "isPreorder",
"value": false
},
{
"name": "siteIds",
"value": [
"LP"
]
},
{
"name": "warrantyServicePeriod",
"value": ""
},
{
"name": "normalizedSize",
"value": "Sin Tamaño"
},
{
"name": "color",
"value": "Azul"
},
{
"name": "department",
"value": "522"
},
{
"name": "isMarketPlace",
"value": false
},
{
"name": "isBackorder",
"value": false
},
{
"name": "normalizedColor",
"value": "Azul Claro"
},
{
"name": "serviceCombo",
"value": false
},
{
"name": "comfortServiceAddress",
"value": ""
},
{
"name": "name",
"value": "SUDADERA REGULAR, XCH, AZUL"
},
{
"name": "warrantyServiceCost",
"value": false
},
{
"name": "comfortServiceCost",
"value": false
},
{
"name": "materialGroup",
"value": "52217"
},
{
"name": "categoryIds",
"value": [
"catst17148143",
"catst54234561"
]
},
{
"name": "warrantyServiceAddress",
"value": ""
},
{
"name": "productType",
"value": "SOFT_LINE"
},
{
"name": "brand",
"value": "PUMA"
},
{
"name": "isProductActive",
"value": true
},
{
"name": "clothingSize",
"value": "XCH"
},
{
"name": "material",
"value": "Algodón"
},
{
"name": "warrantyService",
"value": false
},
{
"name": "isVariantActive",
"value": true
}
],
"assets": [
{
"id": "29e223f4-2a46-4c93-85eb-81473f12aaae",
"sources": [
{
"uri": "https://ss522.liverpool.com.mx/xl/1193114901_1p.jpg",
"key": "1193115744-galleriaImage_DetailImg-94117260",
"contentType": "image/*"
},
{
"uri": "https://ss522.liverpool.com.mx/xl/1193114901_4p.jpg",
"key": "1193115744-galleriaImage_DetailImg-94117265",
"contentType": "image/*"
},
{
"uri": "https://ss522.liverpool.com.mx/xl/1193114901_2p.jpg.jpg",
"key": "1193115744-galleriaImage_DetailImg-94499747",
"contentType": "image/*"
},
{
"uri": "https://ss522.liverpool.com.mx/xl/1193114901_3p.jpg.jpg",
"key": "1193115744-galleriaImage_DetailImg-94499749",
"contentType": "image/*"
},
{
"uri": "https://ss522.liverpool.com.mx/lg/1193114901.jpg",
"key": "1193115744-largeImage",
"contentType": "image/*"
},
{
"uri": "https://ss522.liverpool.com.mx/sm/1193114901.jpg",
"key": "1193115744-smallImage",
"contentType": "image/*"
},
{
"uri": "https://ss522.liverpool.com.mx/xl/1193114901.jpg",
"key": "1193115744-thumbnailImage",
"contentType": "image/*"
}
],
"name": {
"es-MX": "SUDADERA REGULAR, XCH, AZUL"
},
"key": "1193115744",
"tags": []
}
],
"availability": {
"isOnStock": true,
"availableQuantity": 37,