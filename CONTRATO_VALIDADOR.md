# CONTRATO — Normalización y Validación de Órdenes OMS / SOMS

**Interfaz:** `INT200_SL_ATG_APV_OMS_Order_Load` — *EPL OMS SL*
**Origen:** ATG / APV / POS → **Destino:** IBM OMS (Sterling)
**Transporte:** MQSeries JMS asíncrono — cola `QA.VAD.OMS.ORDER.CREATE.IN`
**Servicio SDF:** `EPLCreateOrderAsyncService`
**Proyecto:** Decommission

---

## 1. Propósito

Este documento es el contrato de entrada para un módulo Java que **normaliza y valida** el payload de creación de orden antes de enviarlo a OMS/SOMS. Recoge:

1. La definición formal de la hoja `Input Message` del archivo INT200 (obligatoriedad, longitudes, valores por defecto, catálogos).
2. Los hallazgos empíricos de depuración sobre lotes reales rechazados por SOMS.

Todo lo marcado como **[SPEC]** proviene de la definición. Todo lo marcado como **[EMP]** proviene de la observación de órdenes reales y debe confirmarse con el equipo de OMS antes de convertirse en regla dura.

---

## 2. Regla de codificación

> **[EMP] Presente en 30+ campos de los lotes analizados.**

El payload llega con **mojibake**: texto UTF-8 interpretado como ISO-8859-1.

| Observado | Correcto |
|---|---|
| `Cajonera sÃ­ lima` | `Cajonera sí lima` |
| `Bocina portÃ¡til JBL` | `Bocina portátil JBL` |
| `Salida A PÃ¡tzcuaro` | `Salida A Pátzcuaro` |
| `Boulevard Carlos Pellicet CÃ¡mara` | `Boulevard Carlos Pellicet Cámara` |
| `TreviÃ±o` | `Treviño` |
| `50928â€¯pm.jpg` | `50928 pm.jpg` (U+202F) |

**Campos afectados con más frecuencia:** `Item.ItemDesc`, `PersonInfo*.City`, `PersonInfo*.AddressLine1/4/5/6`, `PersonInfo*.FirstName/LastName`, `Order.CustomerFirstName/LastName`, `Extn.LExtnURLSKU`.

### Requisito

El arreglo de fondo es en el **productor** (`Content-Type: application/json; charset=UTF-8` y evitar `new String(bytes)` sin charset). El módulo debe además aplicar reparación defensiva:

```java
private static final Pattern MOJIBAKE = Pattern.compile("[ÃÂ]|â€");

static String repairEncoding(String value) {
    if (value == null || !MOJIBAKE.matcher(value).find()) return value;
    byte[] raw = value.getBytes(StandardCharsets.ISO_8859_1);
    String repaired = new String(raw, StandardCharsets.UTF_8);
    // Solo aceptar si la reparación no introdujo U+FFFD
    return repaired.indexOf('\uFFFD') >= 0 ? value : repaired;
}
```

**Importante:** aplicar la reparación **una sola vez**, antes de cualquier validación de longitud. Una reparación doble corrompe el texto.

---

## 3. Campos obligatorios por bloque

### 3.1 `/Order` — cabecera

| Campo | Tipo | Long. | Default | Notas |
|---|---|---|---|---|
| `CustomerPhoneNo` | string | 40 | | |
| `CustomerFirstName` | string | 64 | | **[EMP]** ausente en flujo Personal/CNC |
| `CustomerLastName` | string | 64 | | **[EMP]** ausente en flujo Personal/CNC |
| `CustomerEMailID` | string | 60 | | M condicional: requerido para Mesa de Regalos |
| `DocumentType` | string | 40 | `0001` | |
| `EnterpriseCode` | string | 24 | `Liverpool` | |
| `EntryType` | string | 20 | | Catálogo §4.1 |
| `OrderDate` | dateTime | 25 | sysdate | ISO-8601 |
| `OrderName` | string | 15 | | |
| `OrderNo` | string | 40 | | |
| `OrderType` | string | 20 | | Catálogo §4.2 |
| `SellerOrganizationCode` | string | 24 | `Liverpool` | |
| `BillToID` | string | 40 | | M/O ambiguo en la definición |

### 3.2 `/Order/OrderLines/OrderLine`

| Campo | Tipo | Long. | Default | Notas |
|---|---|---|---|---|
| `ConditionVariable1` | string | 40 | **`OMS`** | **[EMP]** vacío en el 100% de MKP |
| `DeliveryMethod` | string | 40 | | Siempre `SHP` (C&C y S2H) |
| `DepartmentCode` | string | 20 | | |
| `LineType` | string | 20 | | Catálogo §4.3 |
| `OrderedQty` | decimal | 14,4 | | |
| `PrimeLineNo` | int | 5 | `1` | |
| `SubLineNo` | int | 5 | `1` | |

### 3.3 `/Order/OrderLines/OrderLine/Item`

| Campo | Tipo | Long. | Notas |
|---|---|---|---|
| `ItemDesc` | string | 500 | Alto riesgo de mojibake |
| `ItemID` | string | 40 | |
| `UnitOfMeasure` | string | 40 | Observado siempre `UN` |

### 3.4 `/Order/OrderLines/OrderLine/LinePriceInfo`

| Campo | Tipo | Long. | Default |
|---|---|---|---|
| `IsPriceLocked` | string | 1 | `N` |
| `UnitPrice` | decimal | 19,6 | `0` |
| `RetailPrice` | decimal | 19,6 | `0` |

> **[EMP]** Los payloads reales envían `ListPrice` en lugar de `RetailPrice`. Confirmar con OMS cuál espera SOMS; si es `RetailPrice`, hay que mapear.

### 3.5 `/Order/PersonInfoShipTo`, `PersonInfoBillTo`, `PersonInfoContact`

Los tres bloques comparten el **mismo conjunto de 10 obligatorios**:

| Campo | Tipo | Long. | Default | Semántica |
|---|---|---|---|---|
| `AddressLine1` | string | 70 | | Calle |
| `AddressLine2` | string | 70 | | Número exterior |
| `AddressLine3` | string | 70 | **`Sin numero en caso de no contar con el dato`** | Número interior y edificio |
| `City` | string | 35 | | |
| `Country` | string | 40 | | `MX` |
| `EMailID` | string | 150 | | |
| `FirstName` | string | 64 | | |
| `LastName` | string | 64 | | |
| `State` | string | 35 | | Código de estado (ej. `SLP`, `TAB`, `CMX`) |
| `ZipCode` | string | 35 | | |

> **El default de `AddressLine3` resuelve el caso más frecuente:** cuando no hay número interior, se envía el literal `Sin numero en caso de no contar con el dato`, **no** vacío. Esto aplica a los tres bloques.

---

## 4. Catálogos de valores

### 4.1 `EntryType`
`WEB` · `APV` · `POSF` · `KIO`

### 4.2 `OrderType`
`Personal` · `Gift Registry` · `MKP`

Longitud declarada 20. El literal `Gift Registry` lleva espacio.

### 4.3 `LineType`
**[SPEC — columna List Values]** solo `SL`
**[SPEC — columna ATG]** `SL` / `SI`

> **Discrepancia dentro de la propia definición.** Adoptar `{SL, SI}` como conjunto permitido y confirmarlo con OMS.
> **[EMP]** Se detectó una orden MKP con `LineType = "BT"` (OrderNo `6840116238`), inválido bajo cualquiera de las dos lecturas.

### 4.4 `FulfillmentType`

| Valor | Significado |
|---|---|
| `Fulfillment_Type_Liverpool` | Ship to Home |
| `Liverpool_CNC_PICK_PACK` | Click and Collect |
| `Toys_Fulfillment_Type_Liverpool` | Toys Ship to Home |
| `Toys_Liverpool_CNC_PICK_PACK` | Toys Click and Collect |
| `Fulfillment_Type_Preventa` | Preventa |
| `Fulfillment_Type_Pedido_Especial` | Pedido especial |
| `Liverpool_Deliver_By_Parcel` | Entrega por paquetería |

> **[SPEC]** Nota textual: *"Not changes, not required anymore from channels. OMS will determined it. Remove in MW."* El campo está en proceso de deprecación; **no** validarlo como obligatorio.
> **[EMP]** Los payloads MKP usan además `LIV_MKP_SHP` y `LIV_MKP_PICK`, ausentes del catálogo. Confirmar si son válidos o legado.

### 4.5 `NotificationType` — marca / boutique

| Código | Marca | | Código | Marca |
|---|---|---|---|---|
| `LIV` | Liverpool | | `BNR` | Banana Republic |
| `SUB` | Suburbia | | `TRU` | Toys R Us |
| `PBA` | Pottery Barn | | `BRU` | Baby R Us |
| `PBK` | Pottery Barn Kids | | `GAL` | Galerías |
| `WEL` | West Elm | | `DPS` | Dupuis |
| `WSO` | Williams Sonoma | | `FAB` | Fabletics |
| `GAP` | Gap | | `DST` | Disney Store |
| `DCK` | Dockers | | `LST` | Livestore |

> **[EMP] Discrepancia observada.** Los payloads reales envían `LP` (Liverpool) y `SB` (Suburbia), que **no** están en el catálogo (`LIV`, `SUB`). `PBK` sí coincide. Como el campo es opcional y las órdenes procesan correctamente, se asume que Middleware mapea o que el catálogo está desactualizado. **Documentar, no rechazar.**

### 4.6 `AllocationRuleID`
**[SPEC]** `FWD_RULES`. Nota: *"[2026/3/26] This is not required from Channels to set. Therefore, optional. Remove in MW."*
**[EMP]** También se observa `EPLSOMSHRL`. Campo opcional, no validar.

### 4.7 Campos que la definición pide vacíos
- `ShipNode` — *"Send empty"*
- `CarrierServiceCode` — *"Empty"*

> **[EMP]** Los payloads CNC sí envían `ShipNode` poblado (`127`, `134`, `0541`). No rechazar; registrar en log.

---

## 5. Reglas de derivación

> **[EMP]** Estas reglas reprodujeron órdenes que SOMS aceptó correctamente. Son **paliativos**: el arreglo definitivo es en fachada.

### 5.1 Orden de precedencia para identidad

Cuando un bloque carece de `FirstName` / `LastName` / `EMailID`, tomar el primer valor no vacío en este orden:

```
PersonInfoShipTo → PersonInfoBillTo → PersonInfoContact → Order.Customer*
```

**Justificación empírica:** en órdenes CNC, `PersonInfoShipTo` contiene la identidad del cliente aunque la dirección sea la de la tienda. En otras, la única fuente es `PersonInfoBillTo`.

### 5.2 Propagación a cabecera

```
Order.CustomerFirstName ← primer FirstName no vacío entre los tres bloques
Order.CustomerLastName  ← primer LastName no vacío
Order.CustomerEMailID   ← primer EMailID no vacío
Order.correoLoggin      ← igual que CustomerEMailID
```

### 5.3 Defaults aplicables

| Campo | Regla |
|---|---|
| `OrderLine.ConditionVariable1` | si vacío → `OMS` |
| `PersonInfo*.AddressLine3` | si vacío → `Sin numero en caso de no contar con el dato` |
| `PersonInfo*.Country` | si vacío → `MX` |
| `LinePriceInfo.IsPriceLocked` | si vacío → `N` |
| `OrderLine.PrimeLineNo` / `SubLineNo` | si vacío → `1` |
| `DocumentType` | si vacío → `0001` |

### 5.4 `State` desde código postal

Si `State` está vacío pero hay `ZipCode`, derivar con tabla de rangos de CP → código de estado (ej. `86190` → `TAB`). Requiere catálogo externo.

### 5.5 Normalización de texto

Aplicar en este orden, a **todos** los campos string:

1. Reparar mojibake (§2)
2. `trim()` de espacios inicial y final
3. Colapsar espacios internos múltiples
4. Convertir `""` en `null` **solo** donde el campo sea opcional
5. Truncar a la longitud máxima **después** de los pasos anteriores

> **[EMP]** Se observaron valores como `"Davila "`, `"Carlos alejandro "`, `"Morelia "` y `"  y Casa"`. No provocan rechazo pero contaminan etiquetas de envío y correos.

### 5.6 Lo que NO se puede derivar

Estos casos deben producir **error duro** y no intentar rellenarse:

| Situación | Razón |
|---|---|
| Ningún `EMailID` en todo el payload | No hay fuente; inventarlo rompe notificaciones |
| `PersonInfoBillTo` con dirección vacía y `City` distinta a ShipTo/Contact | Es un domicilio real diferente, no la tienda |
| `ItemID` ausente | Identificador de producto, no derivable |
| `OrderNo` ausente | Clave de negocio |

---

## 6. Diseño del módulo Java

### 6.1 Contrato de la API

```java
public interface OrderContractProcessor {

    /**
     * Normaliza el payload y lo valida contra INT200.
     * No lanza excepción: el resultado agrega todos los hallazgos.
     */
    ValidationResult process(OrderPayload payload);
}

public record ValidationResult(
    OrderPayload normalized,
    List<Finding> findings,
    boolean sendable          // false si existe al menos un ERROR
) {
    public List<Finding> errors()   { ... }
    public List<Finding> warnings() { ... }
}

public record Finding(
    Severity severity,
    String   path,        // "/Order/OrderLines/OrderLine[0]/ConditionVariable1"
    String   code,        // ver §6.2
    String   message,
    String   originalValue,
    String   appliedValue // no nulo si se aplicó derivación o default
) {}

public enum Severity { ERROR, WARNING, INFO }
```

### 6.2 Códigos de hallazgo

| Código | Severidad | Descripción |
|---|---|---|
| `CTR-001` | ERROR | Campo obligatorio ausente y no derivable |
| `CTR-002` | INFO | Campo obligatorio rellenado con default de la definición |
| `CTR-003` | INFO | Campo obligatorio derivado de otro bloque |
| `CTR-004` | ERROR | Valor fuera de catálogo |
| `CTR-005` | WARNING | Longitud excedida — valor truncado |
| `CTR-006` | INFO | Mojibake reparado |
| `CTR-007` | WARNING | Espacios sobrantes normalizados |
| `CTR-008` | WARNING | Discrepancia conocida pendiente de confirmar con OMS |
| `CTR-010` | WARNING | Campo que la definición pide vacío llega poblado |

### 6.3 Orden de ejecución obligatorio

```
1. repairEncoding          (§2)
2. trim / normalize        (§5.5)
3. applyDerivations        (§5.1, §5.2, §5.4)
4. applyDefaults           (§5.3)
5. validateMandatory       (§3)
6. validateCatalogs        (§4)
7. validateLengths         (§3)
```

Invertir 1↔7 produce truncados incorrectos: los caracteres mojibake ocupan más bytes que el texto reparado.

### 6.4 Estructura sugerida

```
com.liverpool.oms.contract
├── model/           OrderPayload, OrderLine, PersonInfo, Item, LinePriceInfo
├── spec/            FieldSpec, SectionSpec, Int200Registry  (§3 y §4 como datos)
├── normalize/       EncodingRepairer, TextNormalizer, DefaultApplier, Deriver
├── validate/        MandatoryValidator, CatalogValidator, LengthValidator
└── OrderContractProcessorImpl
```

**Principio de diseño:** `Int200Registry` debe cargar §3 y §4 desde un recurso externo (YAML o JSON), no hardcodearlos. La definición cambia — la hoja tiene columna *Revision History* y varias notas fechadas (`rmm.27Oct`, `jun11.rmm`, `[2026/3/26]`). Un cambio de catálogo no debe requerir recompilar.

---

## 7. Casos de prueba

Derivados de órdenes reales. Los tres primeros fueron corregidos y **enviados con éxito a OMS y SOMS**.

| # | OrderNo | Tipo | Condición de entrada | Resultado esperado |
|---|---|---|---|---|
| 1 | `4470116664` | Personal / CNC | Sin `CustomerFirstName`/`LastName`; `PersonInfoContact` sin identidad; `ConditionVariable1` vacío | 4 derivaciones + 1 default → `sendable = true` |
| 2 | `0150172387` | Personal / CNC | Sin correo en **todo** el payload; `BillTo` sin dirección con `City` distinta | `CTR-001` en `EMailID` y `BillTo.AddressLine1` → `sendable = false` |
| 3 | `6840116238` | MKP | `LineType = "BT"`; mojibake en `ItemDesc` y `AddressLine6` | `CTR-004` + 2× `CTR-006` |
| 4 | — | cualquiera | `AddressLine3` vacío | Relleno con el literal de default, no con espacio |
| 5 | — | MKP | `PersonInfoBillTo` completamente vacío | 5× `CTR-001` |

### Regresión de codificación

```java
@Test void repairIsIdempotent() {
    String once  = repairEncoding("Cajonera sÃ­ lima");
    String twice = repairEncoding(once);
    assertEquals("Cajonera sí lima", once);
    assertEquals(once, twice);          // no debe re-procesar
}
```

---

## 8. Patrones de falla observados

Resumen de la validación de 42 órdenes únicas del reporte de reproceso más 3 órdenes individuales.

| Patrón | Alcance | Bloque afectado |
|---|---|---|
| `ConditionVariable1` vacío | **Universal** — todos los tipos de orden | `OrderLine` |
| `PersonInfoBillTo` sin dirección ni correo | Exclusivo de **MKP** (40 de 42) | `PersonInfoBillTo` |
| Cabecera sin `CustomerFirstName`/`LastName` | Exclusivo de **Personal / CNC** | `/Order` |
| `PersonInfoContact` sin identidad | Exclusivo de **Personal / CNC** | `PersonInfoContact` |
| `AddressLine3` vacío | Generalizado en ShipTo y Contact | los tres bloques |
| Mojibake | Transversal, ~30 campos | `Item`, `PersonInfo*` |
| `LineType` fuera de catálogo | 1 caso aislado | `OrderLine` |

### Conclusión estructural

> Los huecos de MKP y los de Personal/CNC son **disjuntos**. Esto indica **dos rutas de armado distintas en fachada**, cada una con su propio conjunto de campos sin poblar. El módulo de normalización mitiga ambos, pero la corrección definitiva son dos fixes separados en el productor.

---

## 9. Puntos abiertos

Pendientes de confirmar con el equipo de OMS antes de fijar reglas duras:

1. **`LineType`** — ¿`{SL}` o `{SL, SI}`? La definición se contradice internamente.
2. **`NotificationType`** — ¿`LP`/`SB` son válidos o deben mapearse a `LIV`/`SUB`?
3. **`FulfillmentType`** — ¿`LIV_MKP_SHP` / `LIV_MKP_PICK` son vigentes? El campo está marcado para deprecación.
4. **`RetailPrice` vs `ListPrice`** — cuál espera SOMS en `LinePriceInfo`.
5. **`ShipNode`** — la definición dice enviarlo vacío, pero CNC lo requiere poblado para el ruteo.
6. **`AddressLine3`** — confirmar que SOMS acepta el literal de default sin validación de formato.
7. **`BillToID`** — la celda de obligatoriedad contiene `M` y `O` simultáneamente.
8. **Código de respuesta `No F001`** — presente en las 130 filas del reporte de reproceso. Determinar si corresponde a validación de esquema (apunta a obligatorios) o a resolución de nodo (apunta a `ShipNode` / `ConditionVariable1`). **De esto depende cuál de los patrones de §8 es la causa real y cuáles son ruido concurrente.**

---

*Fuentes: `INT200_SL_ATG_APV_OMS_Order_Load.xlsx` (hojas Input Message, Assumptions, Metadata & Header, NA Error Message) y análisis de payloads de los lotes de reproceso.*