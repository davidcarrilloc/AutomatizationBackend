# SPEC: INT200 Validador

> Estado: **Implementado**

Necesito que crees una automatización con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y cláusulas necesarias establecidas en CLAUDE.md
- Vas a crear un validador te vas a basar en ReprocesoController en lo que pide el metodo procesarNode

## Brief

**Por qué:** Se debe validar cualquier JSON REQUEST que se te pase y que detectes y valides los nodos que hacen falta, y lo que no respete las reglas del INT200 (INT200_SL_ATG_APV_OMS_Order_Load.xlsx)

**Entrada:** Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=remisión

**Origen:** Vas a construir tu propia logica de negocio a partir de CONTRATO_VALIDADOR.md

**Regla:** En base a las validaciones guardaras lo que necesita, todo concatenado por comas

**Salida:** Las columnas seran las siguientes:  A=JSON, B=remisión, C=validaciones lo que hace falta
- Errores: En la columna D cualquier error que no se tome en cuenta o edge cases
- Trunca lo que pase el límite de Excel (32 767 car.)

Sólo si aplica:
- **Ritmo:** Ocupa el ritmo de las demas implementaciones
- **Job asíncrono:** No es job
- **Reutiliza:** ReprocesoController.procesarNode
- Ejemplo:
  {
  "CustomerEMailID": "fabioeyt11@gmail.com",
  "CustomerPhoneNo": "13534902",
  "CustomerFirstName": "Fabio",
  "CustomerLastName": "Espinosa",
  "CustomerPONo": "4170118758",
  "CustomerRewardsNo": "8582561631",
  "DepartmentCode": "",
  "Division": "",
  "DocumentType": "0001",
  "EnterpriseCode": "Liverpool",
  "EntryType": "WEB",
  "OrderDate": "2026-07-30T03:54:57-06:00",
  "OrderName": "39",
  "OrderNo": "4170118758",
  "OrderType": "Personal",
  "BillToID": "8582561631",
  "SellerOrganizationCode": "Liverpool",
  "AllocationRuleID": "FWD_RULES",
  "correoLoggin": "fabioeyt11@gmail.com",
  "NotificationType": "LP",
  "OrderLines": [
  {
  "OrderLine": {
  "DeliveryMethod": "SHP",
  "DepartmentCode": "201",
  "FulfillmentType": "Liverpool_CNC_PICK_PACK",
  "LExtnFEEBACKORD": "4170118758",
  "Delivery": "CC0D",
  "Store": "72",
  "CarrierServiceCode": "",
  "LineType": "SL",
  "OrderedQty": "1",
  "PrimeLineNo": "1",
  "ShipNode": "72",
  "ConditionVariable1": "",
  "SubLineNo": "1",
  "Extn": {
  "ExtnOfferID": "",
  "ExtnOfferPrice": "",
  "ExtnIsFreeLine": "N",
  "ExtnIsPromotionLine": "N",
  "ExtnLineType": "",
  "ExtnSIItemType": "",
  "ExtnPromiseEDD1": "2026-07-30T00:00:00",
  "ExtnPromiseEDD2": "2026-07-30T00:00:00",
  "LExtnURLSKU": "https://ss201.liverpool.com.mx/xl/1157716014_1p.jpg",
  "ExtnItemService": "",
  "ExtnNoSpotService": null
  },
  "Item": {
  "ItemDesc": "Banda deportiva para entrenamiento Adidas de unisex",
  "ItemID": "1157716014",
  "SupplierItem": "",
  "UnitOfMeasure": "UN"
  },
  "LinePriceInfo": {
  "IsPriceLocked": "Y",
  "UnitPrice": "359.20",
  "ListPrice": "449.00"
  },
  "References": {
  "Reference": [
  {
  "Name": "ExtnReqDeliveryDate",
  "Value": "2026-07-30"
  },
  {
  "Name": "ExtnStore",
  "Value": "39"
  },
  {
  "Name": "ExtnDiscountAmount",
  "Value": "89.80"
  }
  ]
  },
  "ConditionVariable2": "PICK",
  "ReqDeliveryDate": "2026-07-30T00:00:00",
  "ReqDeliveryDate2": "2026-07-30T00:00:00",
  "EarliestScheduleDate": null,
  "DerivedFrom": null
  }
  }
  ],
  "PersonInfoShipTo": {
  "AddressLine1": "0072 LIV Liverpool AngelÃ³polis",
  "AddressLine2": "2510",
  "AddressLine3": "",
  "LExtnAddressLineNINT": "",
  "LExtnAddressLineEDIFICIO": "",
  "AddressLine4": "ConcepciÃ³n La Cruz",
  "AddressLine5": "Puebla",
  "AddressLine6": "",
  "City": "Puebla",
  "Country": "MX",
  "EMailID": "fabioeyt11@gmail.com",
  "FirstName": "Fabio",
  "LastName": "Espinosa",
  "MiddleName": "",
  "MobilePhone": "",
  "DayPhone": "(222) 229-7500",
  "State": "PUE",
  "IsAddressVerified": "N",
  "ZipCode": "72430",
  "LExtnATGADRID": "RECOGE EN MODULO"
  },
  "PersonInfoBillTo": {
  "AddressLine1": "0072 LIV Liverpool AngelÃ³polis",
  "AddressLine3": " ",
  "AddressLine4": "ConcepciÃ³n La Cruz",
  "AddressLine5": "Puebla",
  "AddressLine6": "",
  "City": "PUEBLA",
  "Country": "MX",
  "DayPhone": "13534902",
  "EMailID": "fabioeyt11@gmail.com",
  "FirstName": "Fabio",
  "LastName": "Espinosa",
  "MiddleName": "",
  "MobilePhone": "",
  "State": "PUE",
  "IsAddressVerified": "N",
  "ZipCode": "72000"
  },
  "Extn": {
  "ExtnMKPOrderBy": "Liverpool",
  "ExtnPushEmailID": "fabioeyt11@gmail.com",
  "ExtnOrderType": "",
  "ExtnGREventID": "",
  "ExtnGREventName": "",
  "ExtnGROwnerName": "",
  "ExtnIsGiftPlusPurchase": "N",
  "ExtnIsPromotionPurchase": "Y",
  "ExtnFlashPromo": "Y",
  "ExtnStoreSalesCredit": "0072",
  "ExtnTicketNumber": "0015",
  "LExtnOrderPrice": "359.20",
  "LExtnSexID": "",
  "LExtnCustomerLastNameP": "Espinosa",
  "LExtnCustomerLastNameM": "",
  "ExtnPaymentType": null,
  "ExtnMKPimprint_number": null,
  "ExtnMKPEMail": null,
  "ExtnMKPorder-total-orig": null,
  "ExtnEmployeeNumber": null,
  "ExtnStoreNumber": null,
  "ExtnPickupType": "M"
  },
  "PersonInfoContact": {
  "AddressLine1": "Av. del NiÃ±o Poblano",
  "AddressLine2": "2510",
  "AddressLine3": "",
  "AddressLine4": "ConcepciÃ³n La Cruz",
  "AddressLine5": "Puebla",
  "AddressLine6": "",
  "City": "Puebla",
  "Country": "MX",
  "EMailID": "fabioeyt11@gmail.com",
  "FirstName": "Fabio",
  "LastName": "Espinosa",
  "MiddleName": "",
  "MobilePhone": "",
  "State": "PUE",
  "IsAddressVerified": "N",
  "ZipCode": "72430"
  },
  "References": {
  "Reference": [
  {
  "Name": "ExtnStoreNumber",
  "Value": "0039"
  },
  {
  "Name": "ExtnEmployeeNumber",
  "Value": ""
  },
  {
  "Name": "ExtnStoreSalesCredit",
  "Value": "0072"
  },
  {
  "Name": "ExtnPaymentType",
  "Value": "CREDIT_CARD"
  },
  {
  "Name": "ExtnTerminal",
  "Value": "31"
  },
  {
  "Name": "ExtnTicketNumber",
  "Value": "0015"
  },
  {
  "Name": "SellerName",
  "Value": ""
  },
  {
  "Name": "ExtnComments",
  "Value": ""
  }
  ]
  },
  "OrderDates": {
  "OrderDate": null
  }
  }

A este json le hace falta: PersonInfoBillTo.AddressLine2

---

## Cierre

**Componentes:**

- `src/main/resources/int200-rules.json` — registro externo de las reglas del INT200: bloques obligatorios con sus campos, longitudes, valores por default, catálogos, campos que la definición pide vacíos y discrepancias conocidas. Cambiar un catálogo no requiere recompilar.
- `model/Int200Rules.java` — records anidados (`Bloque`, `Campo`) que mapean ese JSON.
- `model/ValidacionRow.java` — fila de entrada: `json`, `remision`.
- `model/ValidacionResult.java` — fila de salida: `json`, `remision`, `faltantes`, `errores`.
- `service/ValidadorService.java` — carga las reglas una vez al arrancar y valida fila por fila.
- `service/ExcelService.java` — se agregaron `leerValidacion` y `crearReporteValidacion`.
- `controller/ValidadorController.java` — `POST /api/v1/validador/procesar`.
- `docs/Documentacion_Endpoints.md` — sección `## Validador`.

**Hallazgos:**

1. **El criterio de obligatoriedad sale del cruce de dos columnas, no de una.** En la hoja `Input Message` (rango `A5:Q1409`) un campo solo cuenta como obligatorio si su fila tiene `Used=Y` **y** `O/M=M`, **y además** la fila de sección que lo encabeza cumple lo mismo. Tomar solo `O/M=M` genera falsos positivos que tumban el resultado esperado de este spec:
   - `PersonInfoContact` (r806) tiene sección `Used` vacío y `O/M=O` → no se valida, aunque `CONTRATO_VALIDADOR.md` §3.5 lo liste junto a ShipTo y BillTo.
   - `OrderLine.Extn` (r259) es sección `O`, así que sus hijos `M` (`ExtnMultiSiteType`, `LExtnCurrentIsoCode`, `LExtnsku-detail`) no se exigen.
   - `LinePriceInfo.RetailPrice` (r407) y `PersonInfo*.DayPhone` (r732/r763) son `M` pero `Used=N` → fuera. Esto resuelve de paso el punto abierto §9.4 del contrato: SOMS no espera `RetailPrice` en este flujo.
   - `PersonInfoShipTo` aparece dos veces (r683 `Used=N`, r752 `Used=Y`); vale la segunda.
   - `AddressLine4`/`AddressLine5` traen la celda `O/M` con el texto `"O M"`: se leen como opcionales. `BillToID` (r111) trae `"M\nO"`: se lee como obligatorio (punto abierto §9.7).

2. **Falta ≠ vacío.** El JSON de ejemplo trae `ConditionVariable1: ""`, `PersonInfoShipTo.AddressLine3: ""` y `PersonInfoBillTo.AddressLine3: " "`, y aun así el resultado esperado es solo `PersonInfoBillTo.AddressLine2`. Los tres tienen default en la definición. La regla que reproduce el resultado exacto: un obligatorio va a la columna `Validaciones` si **la clave no existe**, o si está vacía **y no tiene default**; si está vacía **con** default va a `Errores` como `CTR-002`. Sin esta distinción el reporte marca huecos que en realidad se rellenan solos al armar el mensaje.

3. **`References/Reference.Value` es `M` en la hoja pero llega vacío legítimamente** (`SellerName`, `ExtnComments`, `ExtnEmployeeNumber`). Marcarlo agregaba tres falsos positivos por orden. Solo se exige `Name`.

4. **El mojibake se revisa en todo el payload, no solo en los obligatorios.** El contrato §2 lo reporta en ~30 campos, muchos opcionales (`AddressLine4`, `PersonInfoContact.*`). El recorrido es una pasada recursiva sobre todas las hojas de texto. Este endpoint **detecta pero no repara**: es diagnóstico, la reparación va en fachada.

5. No se implementó el diseño de `CONTRATO_VALIDADOR.md` §6.4 (`OrderContractProcessor`, `Finding`, `Severity`, 5 paquetes). Está dimensionado para el módulo de normalización de fachada; aquí la salida son dos celdas de texto y un record de seis campos no sobrevive al `String.join`.

**Verificación:**

Compilado con JDK 23 (`JAVA_HOME` global apunta a JDK 11). Se ejecutó `ValidadorService` directamente contra el JSON de ejemplo de este spec, en cuatro casos:

| Caso | `Validaciones` obtenido |
|---|---|
| JSON del spec, sin envoltura | `PersonInfoBillTo.AddressLine2` |
| El mismo JSON envuelto en `{"Order": ...}` | `PersonInfoBillTo.AddressLine2` (idéntico: la envoltura se tolera en la entrada) |
| Texto que no es JSON | vacío; `Errores` = `JSON inválido: ...` |
| `PersonInfoBillTo` ausente por completo | `PersonInfoBillTo` (ruta del bloque) |

En el primer caso `Errores` trae los tres `CTR-002` esperados (`ConditionVariable1`, ambos `AddressLine3`), `CTR-010` por `ShipNode: "72"`, `CTR-008` por `NotificationType: "LP"` y seis `CTR-006` por el mojibake de `AngelÃ³polis`, `ConcepciÃ³n` y `NiÃ±o`.

Falta la prueba de extremo a extremo por Swagger: subir un `.xlsx` de dos columnas a `POST /api/v1/validador/procesar` y revisar el archivo descargado.

