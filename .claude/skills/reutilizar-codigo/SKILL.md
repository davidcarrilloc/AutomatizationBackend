---
name: reutilizar-codigo
description: Úsala ANTES de escribir cualquier clase o método nuevo en este backend, y siempre que arranques un SPEC. Dice qué molde ya existe para lo que vas a hacer — leer un Excel de N columnas, generar el .xlsx de reporte, validar el archivo subido, devolver la descarga desde el controller, modelar el estatus de un job, mapear un error a HTTP, normalizar remisiones. Evita reescribir helpers que ya viven en ExcelService o duplicar lógica entre controllers.
---

# Qué ya existe en este repo

La pregunta con la que arranca cada spec es *¿qué de esto ya está hecho?*. Esta es la respuesta.

`CLAUDE.md` manda en **cómo** se escribe cada capa (anotaciones, nombres en español, logs). Esta
skill dice **qué copiar**: el archivo y el método que ya resuelven tu caso. No hay números de línea
a propósito — se mueven; los nombres no.

## Tabla: qué necesito → qué copio

| Necesito | Molde | Notas |
|---|---|---|
| Leer un Excel de 1 columna | `ExcelService.leerRemisionesDeExcel` | Devuelve `List<String>` |
| Leer un Excel de 2 columnas | `ExcelService.leerValidacion` | Filtra encabezados con `!json.startsWith("{")` |
| Leer un Excel de 2 col. donde una fila produce N registros | `ExcelService.leerMarketplace` + `ValidadorService.extraerMarketplace` | El fan-out se hace con `flatMap` en el servicio, no al leer |
| Leer un Excel con filtro numérico | `ExcelService.leerAvailability` | `!sku.matches("\\d+")` descarta encabezados sin configurar nada |
| Leer un Excel genérico por hoja/columna | `ExcelService.fromExcelToListOfRows` | Recibe hoja y columna como `String` |
| Escribir un reporte `.xlsx` | `ExcelService.crearReporteValidacion` | Molde mínimo: `XSSFWorkbook` + encabezado + filas |
| Que una celda larga no tumbe el reporte | `ExcelService.truncarCelda` (privado) | **Toda** celda de texto pasa por ahí. El límite de Excel es 32 767 char |
| Validar que el archivo subido sea Excel | `ExcelService.esArchivoNoExcel` | Ver la advertencia de abajo |
| Devolver un `.xlsx` como descarga | `ValidadorController.validarOrdenes` | `Content-Disposition: attachment` + `MediaType.parseMediaType(...)` |
| Job asíncrono con `jobId` | skill **`job-asincrono`** | Ya implementado en Fulfillment y Availability |
| Modelar el estatus de un job | `model/EstatusFulfillment` | Úsalo tal cual, **no** crees otro modelo de estatus |
| Executor para `@Async` | `AsyncConfig.fulfillmentExecutor` | Ya compartido por dos módulos; no agregues otro bean |
| Mapear una excepción a un HTTP | `exception/ControllerAdvice` | `IllegalArgumentException` ya devuelve **400**: no crees una excepción nueva para eso |
| Normalizar remisión / tracking a 10 dígitos | `FulfillmentService.rellenarDiezDigitos` | **Privado**: son 3 líneas, se copian (ya está duplicado en `AvailabilityService`). Excel se come el `0` inicial; es la causa #1 de "no encuentra la orden" |
| Consultar un API externa | skill **`api-externa`** | WebClient, OAuth, límite del codec |
| Consultar Oracle o SQLite | skill **`consulta-oracle`** | Qué datasource tiene qué |
| Meter una gráfica al reporte | skill **`graficos-excel`** | POI nativo, no Python |
| Probar la lógica sin arrancar la app | skill **`verificacion-local`** | Los 4 Oracle no responden desde la máquina de desarrollo |

## Módulos completos que sirven de molde

Cuando el spec se parece entero a algo que ya existe, copia el módulo, no el método:

- **Entra Excel → sale Excel, síncrono** (una sola respuesta con la descarga):
  `ValidadorController` + `ValidadorService`. El más corto de todos; empieza aquí si dudas.
- **Entra Excel → job asíncrono → sale Excel**: `AvailabilityController` + `AvailabilityService`,
  o `FulfillmentController` + `FulfillmentService`.
- **Extracción con fan-out** (una fila de entrada, varias de salida):
  `ValidadorService.extraerMarketplace`.
- **Validación de un JSON contra reglas externas**: `ValidadorService.validar` +
  `resources/int200-rules.json`. Las reglas viven en un JSON, no en el código: cambiar un catálogo
  no recompila.

## La regla: búscalo antes de escribirlo

Los métodos van en español y con verbo al principio, así que un `Grep` los encuentra en segundos:

```
Grep  pattern="leer|crear|obtener|truncar|validar"  path="src/main/java/.../service"
```

**Esto no es teoría.** Hoy mismo hay **cuatro** copias privadas del mismo `isNotExcelFile` en
`FulfillmentController`, `OMSController`, `SOMSController` y `UploadController`, mientras
`ExcelService.esArchivoNoExcel` hace exactamente lo mismo y es público. Cuatro lugares que arreglar
el día que haya que aceptar `.csv`. Si vas a tocar uno de esos controllers, cámbialo al público de
paso; si escribes uno nuevo, inyecta `ExcelService` y usa el que ya existe.

## Antes de agregar un modelo

- ¿Es el estatus de un job? → `EstatusFulfillment`. Sus campos se llaman `totalTrackings` y
  `trackingActual`, pero una remisión **es** un tracking number: Availability lo reusa sin cambiarle
  nada. No crees `EstatusAvailability`.
- ¿Es una fila de entrada o de salida de Excel? → un `@Data @NoArgsConstructor @Builder
  @AllArgsConstructor` de campos planos, como `ValidacionRow` / `MarketplaceResult`. Sin lógica.

## Antes de agregar una propiedad

`application.properties` ya tiene los prefijos `copomex.*`, `reproceso.*`, `availability.*`,
`soms.*`, y los 4 datasources. Si tu valor no cambia nunca, **no lo hagas configurable**: una
constante en el servicio se lee mejor y no hay que documentarla. Si sí cambia, usa el prefijo del
módulo y `@Value` en el constructor.
