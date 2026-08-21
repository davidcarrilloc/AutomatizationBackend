# SPEC: <módulo>

> Estado: **Pendiente** → cámbialo a **Implementado** y llena el Cierre al terminar.

Necesito que crees una automatización con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y cláusulas necesarias establecidas en CLAUDE.md

## Brief

**Por qué:** <problema de negocio, 1 línea. Es lo único que no se deduce leyendo el código>

**Entrada:** <excel de N columnas / zip de exceles / query params. Di qué trae cada columna>

**Origen:** <query a bridgecore | endpoint REST | SOAP. Pega el request de ejemplo>
URLs y llaves van en `application.properties`, no las pegues aquí.

**Regla:** <transformación exacta. ¿Coincidencia exacta o patrón?>
- Filas que NO aplican: <se procesan igual / se omiten y se marcan como "...">

**Salida:** <columnas del reporte y de dónde sale cada una | gráficos y qué comparan>
- Errores: <en qué columna quedan>
- Trunca lo que pase el límite de Excel (32 767 car.)

Sólo si aplica:
- **Ritmo:** <una por una con pausa de N s / lotes de N / reintento ante error X, N veces>
- **Job asíncrono:** <sí — endpoints de estatus y descarga por jobId / no — respuesta directa>
- **Reutiliza:** <controller o servicio existente que ya haga algo parecido>
- **Ejemplo:** <payload ANONIMIZADO — sin correos, teléfonos ni domicilios reales>

## Skills

Marca las que apliquen y borra el resto. Cada una carga un patrón ya resuelto en este repo para no
volver a derivarlo; `CLAUDE.md` sigue mandando en las convenciones de cada capa.

- **Siempre, antes de escribir código** → **`reutilizar-codigo`**. Tabla de *qué necesito → qué
  molde copio*: leer el Excel de entrada, generar el `.xlsx`, validar el archivo subido, devolver la
  descarga, modelar el estatus de un job. Es lo que evita reescribir un helper que ya existe.

- **Caso A — el spec pide un job con `jobId`** ("devuélveme un jobId", "déjame consultar el avance",
  respuesta 202, descarga aparte) → **`job-asincrono`**. Los 4 endpoints del patrón, el
  `@Lazy` self-injection sin el cual `@Async` corre síncrono, y el manejo de errores de gateway.

- **Caso B — el spec consulta un servicio externo** (Apigee, OGCP, commercetools, COPOMEX, SOMS)
  → **`api-externa`**. Molde de `WebClient`, OAuth `client_credentials`, el límite de 256 KB del
  codec que revienta con respuestas grandes, y dónde va (y dónde no) un secret.

- **Caso C — el spec consulta base de datos** (Oracle o la SQLite local) → **`consulta-oracle`**.
  Qué datasource tiene qué tabla, el formato de `queries.properties` y sus trampas, molde de
  repository.

- **Caso D — el spec trae lógica de extracción, validación o parseo con ramas** →
  **`verificacion-local`**. Cómo probarla con aserciones sin arrancar la app: los 4 datasources
  Oracle no responden desde la máquina de desarrollo, así que la prueba por Swagger casi nunca es
  opción.

- **Caso E — el reporte lleva gráficas** → **`graficos-excel`**. POI nativo, no Python.

## Cierre (al terminar — esto es lo que vuelve el spec documentación)

**Componentes:** <archivos creados/tocados, una línea cada uno>

**Hallazgos:** <tropiezos y por qué se resolvieron así. Lo que costaría horas redescubrir. Si es transversal, va también a `docs/Hallazgos_Tecnicos.md`>

**Verificación:** <cómo se probó>
