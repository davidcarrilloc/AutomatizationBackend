# SPEC: Validador Marketplace

> Estado: **Implementado**

Necesito que crees una automatización con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y cláusulas necesarias establecidas en CLAUDE.md

## Brief

**Por qué:** Debemos validar que mandamos a Entrada Unica, te voy a pasar varios REQUEST, vas a extraer datos y me vas a regresar un excel con las condiciones de los demas puntos

**Entrada:** Te voy a pasar un Columna A = Request, Columna B = Tracking number, vas a leer registro por registro

**Regla:** Vas a extraer lo siguiente:
"commercial_id": "0110115737", -> Remision
"offer_id": "1113536506", -> OfferId
{
"value": "1199759058",
"code": "product-sap-sku-id"
}, -> Sku

- Todas las filas se procesan igual

**Salida:** La salida es:
Columna A = Remision
Columna B = OfferId
Columna C = Sku

**Importante** Si un REQUEST de una remision tiene varios skus y varios offerid pon cada relacion en un registros, luego otro registro para otra relacion y asi, es decir si aparecen 3 skus de una sola remision, van a haber 3 registros en el excel, misma remision pero diferentre skus y offerids

- Errores: Vas a crear una columna nueva para cuando no encuentres un sku o algun dato faltate o haya algun error
- Trunca lo que pase el límite de Excel (32 767 car.)

Sólo si aplica:
- **Ritmo:** Si hay error deja 100ms sino continua normal, sin un ritmo predeterminado
- **Job asíncrono:** No
- **Reutiliza:** Existen muchos modulos similares, reutiliza el que mas te convenga
- **Ejemplo:**

Dado este REQUEST:
{
"channel_code": "3",
"commercial_id": "0110115737",
"customer": {
"billing_address": {
"city": "MERIDA",
"country": "MEX",
"country_iso_code": "MX",
"firstname": "Juan Gabriel ",
"lastname": "Martin ",
"phone": "91066884",
"state": "YUC",
"street_1": "NumeroInt: ",
"street_2": "Municipio:MERIDA",
"zip_code": "97315"
},
"customer_id": "29852601335",
"email": "juangabrielmartincasanova@gmail.com",
"firstname": "Juan Gabriel ",
"lastname": "Martin ",
"locale": "es_MX",
"shipping_address": {
"city": "loc santa cruz Palomeque ",
"country": "MX",
"country_iso_code": "MEX",
"firstname": "Juan ",
"lastname": "Martin ",
"phone": "9992790875",
"state": "YUCATAN",
"street_1": "Calle:Calle 86a Por 191 Y 191A 531B Santa Cruz Palomeque MÃ©rida YucatÃ¡n.,NumeroInt:531B",
"street_2": "Colonia:SANTA CRUZ,Municipio:loc santa cruz Palomeque ",
"zip_code": "97295"
},
"curp": "",
"rfc": ""
},
"offers": [
{
"currency_iso_code": "MXN",
"leadtime_to_ship": 3,
"delivery_date": {},
"offer_id": "1113536506",
"offer_price": 410.0,
"order_line_additional_fields": [
{
"value": "0.00",
"code": "monto-liver-promo"
},
{
"value": "(3 MSI.)",
"code": "liver-promo-label"
},
{
"value": "410.00",
"code": "offer-original-price"
},
{
"value": "1199759058",
"code": "product-sap-sku-id"
},
{
"value": "false",
"code": "reembolso-parcial"
},
{
"value": "MKPSL",
"code": "tipo-articulo"
},
{
"value": "Blanco",
"code": "sku-detail"
}
],
"price": 410.0,
"quantity": 1,
"shipping_price": 0.0,
"shipping_type_code": "Home"
}
],
"order_additional_fields": [
{
"value": "false",
"code": "reembolso-total"
},
{
"value": "decom",
"code": "Decom"
},
{
"value": "039",
"code": "id-pl-store"
},
{
"value": "Calle 86a Por 191 Y 191A 531B Santa Cruz Palomeque MÃ©rida YucatÃ¡n.",
"code": "calle-cliente"
},
{
"value": "SANTA CRUZ",
"code": "colonia-cliente"
},
{
"value": "loc santa cruz Palomeque ",
"code": "delegacion-municipio-cliente"
},
{
"value": "Casa",
"code": "edificio-cliente"
},
{
"value": "191 Y 191A",
"code": "num-ext-cliente"
},
{
"value": "531B",
"code": "num-int-cliente"
}
],
"payment_info": {
"payment_type": "CREDIT_CARD"
},
"payment_workflow": "PAY_ON_ACCEPTANCE",
"scored": true,
"shipping_zone_code": "Mexico"
}


Debes extraer:
Remision:     "commercial_id": "0110115737",
OfferId:             "offer_id": "1113536506",
Sku:   {
"value": "1199759058",
"code": "product-sap-sku-id"
},

## Cierre

**Componentes:**

- `model/MarketplaceRow.java` — fila de entrada: `request`, `trackingNumber`.
- `model/MarketplaceResult.java` — registro de salida: `remision`, `offerId`, `sku`, `errores`.
- `service/ValidadorService.java` — se agregaron `extraerMarketplace` (público) y `extraerFila` (privado, devuelve **lista** porque una fila se abre en un registro por offer), más los dos ayudantes `texto` y `unir`. Reusa el `ObjectMapper` que el servicio ya tenía inyectado.
- `service/ExcelService.java` — se agregaron `leerMarketplace` y `crearReporteMarketplace`, calcados de `leerValidacion`/`crearReporteValidacion`.
- `controller/ValidadorController.java` — `POST /api/v1/validador/marketplace`, misma forma síncrona que `/procesar`: entra el archivo, sale el `.xlsx` en la misma respuesta.
- `docs/Documentacion_Endpoints.md` — subsección `### POST /marketplace` dentro de `## Validador`.

Sin dependencias nuevas, sin propiedades nuevas, sin SQL, sin excepción nueva (`IllegalArgumentException` ya está mapeada a 400 en `ControllerAdvice`).

**Hallazgos:**

1. **La pausa de 100 ms del spec no aplica y se descartó (decisión confirmada con el usuario).** El ritmo existe para no ahogar una API externa; aquí no hay ninguna llamada de red: el REQUEST ya viene en la columna A y todo el trabajo es `readTree` en memoria. Un `Thread.sleep(100)` por fila con error solo alarga el request — 1 000 filas malas serían 100 s de espera pura sin proteger nada. Si algún día esto llegara a consultar un servicio, la pausa vuelve.

2. **El tracking number de la columna B no cabe en la salida pedida.** El spec lo pide como entrada pero define la salida como Remisión/OfferId/Sku. Se lee y se conserva en `MarketplaceRow` porque es lo único que identifica la fila original cuando el REQUEST no parsea (`log.warn` con el tracking), pero no se escribe en el reporte. La remisión sale del `commercial_id` del propio JSON, no de esa columna.

3. **Ninguna fila de entrada se pierde (decisión del usuario).** Cada REQUEST produce al menos un registro. `commercial_id` faltante es un problema de la fila completa, así que su mensaje se repite en **todos** los registros que esa fila genere; `offer_id` y `product-sap-sku-id` faltantes son del offer y solo manchan su propio registro. Los motivos que coinciden se unen con comas, igual que hace `validarFila`. La alternativa —omitir el registro incompleto— deja al usuario sin manera de saber qué offer se cayó.

4. **El sku no está en la raíz del offer, está en una lista de pares `code`/`value`.** `order_line_additional_fields` mezcla promociones, precios y etiquetas; hay que recorrerla buscando `code == "product-sap-sku-id"`. Se toma el primero que empate. Es la razón por la que la extracción no se puede resolver con un `path()` directo.

**Verificación:**

Compilado con JDK 23 (`JAVA_HOME` global apunta a JDK 11): `BUILD SUCCESS`.

Se ejecutó `ValidadorService.extraerMarketplace` directamente, con 19 aserciones sobre seis casos, todas en verde:

| Caso | Resultado |
|---|---|
| El REQUEST de ejemplo de este spec | 1 registro: `0110115737` / `1113536506` / `1199759058`, `Errores` vacío |
| El mismo REQUEST con 3 offers distintos | 3 registros, misma remisión, cada offerId con su sku (no se cruzan) |
| Offer sin `product-sap-sku-id` | registro emitido con remisión y offerId llenos, `Sku` vacío, `Errores` = `Falta product-sap-sku-id` |
| Texto que no es JSON | 1 registro, `Errores` = `JSON inválido: ...` |
| `commercial_id` vacío con 2 offers | 2 registros, ambos con `Errores` = `Falta commercial_id` |
| REQUEST sin `offers` | 1 registro conservando la remisión, `Errores` = `El REQUEST no trae offers` |

Falta la prueba de extremo a extremo por Swagger: subir un `.xlsx` de dos columnas a `POST /api/v1/validador/marketplace` y revisar el archivo descargado. Requiere arrancar la app, que abre los cuatro datasources Oracle no alcanzables desde esta máquina — mismo pendiente que quedó en `SPEC_VALIDATOR.md` y `SPEC_AVAILABILITY.md`.
