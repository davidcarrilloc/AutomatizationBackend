# SPEC: PersonInfoBillTo

> Estado: **Implementado** → `POST /api/v1/reproceso/billto/procesar`

Necesito que crees una automatización con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y cláusulas necesarias establecidas en CLAUDE.md
- Basate en el endpoint ReprocesoController.procesarNode, de hecho ahi agrega el nuevo endpoint

## Brief

**Por qué:** El problema es que se tiene que reprocesar las remisiones cargando datos de PersonInfoShipTo a PersonInfoBillTo ya que PersonInfoBillTo es obligatorio

**Entrada:** Igual que ReprocesoController.procesarNode

**Regla:** 
- Se va a transformar de la siguiente manera:
Los datos en personinfoshipto:
  "PersonInfoShipTo": {
  "AddressLine1": "Alberto Balderas",
  "AddressLine2": "36",
  "AddressLine3": "4 y Edificio dectres pisis colir blanco",
  "LExtnAddressLineNINT": "4",
  "LExtnAddressLineEDIFICIO": "Edificio dectres pisis colir blanco",
  "AddressLine4": "LIENZO CHARRO",
  "AddressLine5": "HIDALGO",
  "AddressLine6": "Matamoros|Juan Silveti",
  "City": "HIDALGO",
  "Country": "MX",
  "EMailID": "guilleev77@gmail.com",
  "FirstName": "Guillermina ",
  "LastName": "Vargas ",
  "MiddleName": "",
  "MobilePhone": "(443) 331-2373",
  "DayPhone": "(443) 331-2373",
  "State": "MCH",
  "IsAddressVerified": "N",
  "ZipCode": "61170",
  "LExtnATGADRID": ""
  },
- Va a pasar a PersonInfoBillTo tal que:
  "PersonInfoBillTo": {
  "AddressLine1": "Alberto Balderas",
  "AddressLine2": "36",
  "AddressLine3": "4 y Edificio dectres pisis colir blanco",
  "AddressLine4": "LIENZO CHARRO",
  "AddressLine5": "HIDALGO",
  "AddressLine6": "Matamoros|Juan Silveti",
  "City": "MORELIA",
  "Country": "MX",
  "DayPhone": "(000) 000-0000",
  "EMailID": "guilleev77@gmail.com",
  "FirstName": "Guillermina ",
  "LastName": "Vargas ",
  "MobilePhone": "(443) 331-2373",
  "MiddleName": " ",
  "State": "MCH",
  "IsAddressVerified": "N",
  "ZipCode": "58130"
  },
- Si falta algo como         "State": "", o MiddleName o EMailID o algun nodo que no pueda ser encontrado en personinfoshipto, anotalo en la columna de salida C el campo que falta

**Salida:** Igual que ReprocesoController.procesarNode, en caso de error en C, o en caso de nodo faltante en C, en caso de poder cubrir todo el requerimieto obligatorio, poner Enviado o algo homologado que sea respuesta exitosa

Sólo si aplica:
- **Ritmo:** sin pausa o con pausa de 100ms, depende de como este procesarNode
- **Job asíncrono:** no
- **Reutiliza:** debe ser nuevo en ReprocesoController.procesarNode

## Skills

Marca las que apliquen y borra el resto. Cada una carga un patrón ya resuelto en este repo para no
volver a derivarlo; `CLAUDE.md` sigue mandando en las convenciones de cada capa.

- **Siempre, antes de escribir código** → **`reutilizar-codigo`**. Tabla de *qué necesito → qué
  molde copio*: leer el Excel de entrada, generar el `.xlsx`, validar el archivo subido, devolver la
  descarga, modelar el estatus de un job. Es lo que evita reescribir un helper que ya existe.

- **Caso D — el spec trae lógica de extracción, validación o parseo con ramas** →
  **`verificacion-local`**. Cómo probarla con aserciones sin arrancar la app: los 4 datasources
  Oracle no responden desde la máquina de desarrollo, así que la prueba por Swagger casi nunca es
  opción.

## Decisiones

| Punto | Decisión |
|---|---|
| Ruta | `POST /api/v1/reproceso/billto/procesar` |
| Entrada / salida | Idénticas a `procesarNode`: Excel A=JSON, B=TrackingNumber → `.xlsx` de 3 columnas |
| Dirección | ShipTo → BillTo, **solo sobre los campos que BillTo trae vacíos** |
| Campos | Los de ShipTo menos los tres `LExtn*` |
| "Vacío" | Llave ausente, `null`, `""` o solo espacios |
| Obligatorios | Los 10 del bloque `PersonInfoBillTo` de `int200-rules.json` |
| Falta un obligatorio | No se envía; la columna C dice cuáles |
| Sin `PersonInfoShipTo` | No se envía; `Falta el nodo PersonInfoShipTo, no hay de donde copiar` |
| Defaults (`Country`, `AddressLine3`) | Se aplican, la fila sí se envía y C lo anota |
| Columna C exitosa | `Enviado \| <respuesta de I200>` |
| Ritmo | El de `ReprocesoService` (1 s entre envíos, 4 s cada 10). No se tocó |

## Cierre

**Componentes:**
- `service/ReprocesoBillToService.java` — el merge, la revisión de obligatorios y la marca `Enviado`
- `controller/ReprocesoController.java` — tercer endpoint `POST /billto/procesar`
- `docs/Documentacion_Endpoints.md` — subsección con las cuatro formas de la columna `Response`

Sin tocar: `ReprocesoService`, `ReprocesoNodeService`, `ExcelService`, `int200-rules.json`,
`application.properties`. El lector del Excel (`leerReprocesoNode`), el reporte
(`crearReporteReproceso`), el modelo de fila (`ReprocesoNodeRow`) y el motor de envío con pausas se
reutilizaron tal cual: el módulo nuevo es un solo servicio.

**Hallazgos:**
1. **No es una copia de nodo, es un merge.** El ejemplo del spec confunde: el `PersonInfoBillTo`
   destino trae `City` y `ZipCode` que **no** vienen del ShipTo. La razón es que ya tenían valor
   propio y se respetan; solo se rellena lo vacío. Implementarlo como copia habría pisado la
   dirección de facturación real de las órdenes que sí la traen.
2. **La lista de obligatorios ya vivía en el repo.** `int200-rules.json` tiene el bloque
   `PersonInfoBillTo` con sus 10 campos y sus `valorDefault`; es la misma que usa `ValidadorService`.
   No se codificó ninguna lista en Java: si cambia el contrato, se edita el JSON.
3. **`DayPhone` no es obligatorio aunque el INT200 lo marque M.** La nota de `int200-rules.json` lo
   explica: `Used=N`. Se copia si está vacío, pero no bloquea el envío.
4. **La marca `Enviado` se agrega después del envío, no antes.** `ReprocesoService` es genérico y
   devuelve la respuesta cruda; el servicio zipea sus resultados con las filas por índice (el motor
   emite un resultado por fila, en orden) y prefija solo las que realmente salieron. Las filas
   enviadas se anotan en un `IdentityHashMap` **local a la llamada** — el servicio es singleton y
   `ReprocesoNodeRow` es `@Data`, así que dos filas iguales colisionarían en un `HashMap` normal.
5. **Se tolera la llave envolvente `Order`**, igual que `ValidadorService.validarFila`: ATG manda el
   pedido sin ella y la fachada con ella. Como el `ObjectNode` se muta en su lugar, serializar la
   raíz devuelve el JSON en el mismo formato en que llegó.
6. **Segunda copia del cargador de reglas.** `ValidadorService` y `ReprocesoBillToService` leen
   `int200-rules.json` cada uno en su constructor (6 líneas). Si aparece un tercer consumidor, vale
   promoverlo a un `@Bean` de `Int200Rules`; con dos no paga.

**Verificación:**
- `mvn clean package -DskipTests` con JDK 23 → BUILD SUCCESS.
- 25 aserciones sobre `preparar` (skill `verificacion-local`, sin red: el envío vive en
  `ReprocesoService`), todas verdes: BillTo ausente se crea completo, las tres `LExtn*` no viajan,
  `City`/`ZipCode` propios se respetan mientras lo vacío se rellena, `" "` y `null` cuentan como
  vacío, `State` vacío en ambos nodos detiene el envío, `Country` vacío aplica el default y sí
  envía, sin `PersonInfoShipTo` (o con él vacío) se reporta el nodo faltante, JSON inválido se
  reporta como error, y con la llave `Order` el resultado es el mismo y la llave se conserva.
- **Pendiente:** prueba por Swagger. Subir un Excel de ~3 filas a
  `POST /api/v1/reproceso/billto/procesar` y confirmar el `.xlsx` de 3 columnas con las cuatro
  formas de la columna C. Arrancar la aplicación abre los 4 datasources Oracle que no responden
  desde la máquina de desarrollo.
