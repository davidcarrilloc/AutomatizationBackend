# SPEC: BUSQUEDA DE ERRORES

> Estado: **Pendiente** → cámbialo a **Implementado** y llena el Cierre al terminar.

Necesito que crees una automatización con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y cláusulas necesarias establecidas en CLAUDE.md

## Brief

**Por qué:** Se necesita detectar todo el universo que no aparece en audit_log 

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

## Cierre (al terminar — esto es lo que vuelve el spec documentación)

**Componentes:** <archivos creados/tocados, una línea cada uno>

**Hallazgos:** <tropiezos y por qué se resolvieron así. Lo que costaría horas redescubrir. Si es transversal, va también a `docs/Hallazgos_Tecnicos.md`>

**Verificación:** <cómo se probó>
