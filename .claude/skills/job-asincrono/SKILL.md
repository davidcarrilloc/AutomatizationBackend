---
name: job-asincrono
description: Úsala cuando el SPEC pida un proceso masivo que no puede responder en una sola petición — "devuélveme un jobId", "déjame consultar el avance", "202 Accepted", "descarga el Excel cuando termine" — o cuando un método @Async no esté corriendo en segundo plano y el request se quede colgado. Cubre el patrón completo ya implementado en Fulfillment y Availability: los 4 endpoints, el @Lazy self-injection sin el cual @Async no funciona, el manejo de errores de gateway y el reproceso por rondas.
---

# Patrón de job asíncrono con `jobId`

Implementado dos veces: `FulfillmentService` (el original) y `AvailabilityService` (el más completo,
con token y pausas configurables). Cuando el SPEC diga **"Job asíncrono: sí"**, copia uno de esos dos.

## Cuándo aplica

Cuando una corrida tarda más de lo que un HTTP aguanta: cientos de filas contra un API externa con
pausas entre cada una. Si el trabajo es local (parsear JSON, transformar un Excel), **no lo hagas
asíncrono** — `ValidadorController` procesa y devuelve el `.xlsx` en la misma respuesta, y es mucho
menos código.

## Los 4 endpoints

| Endpoint | Devuelve |
|---|---|
| `POST /<recurso>` | `202 Accepted` + el estatus inicial con el `jobId` |
| `GET /<recurso>/estatus/{jobId}` | `200` con el avance de ese job |
| `GET /<recurso>/jobs` | `200` con **todos** los jobs, del más reciente al más viejo |
| `GET /<recurso>/excel/{jobId}` | `200` con el `.xlsx` de resultados |

Los cuatro son parte del patrón: sin `/jobs` no hay forma de recuperar un `jobId` que se perdió ni
de saber qué está corriendo.

## El servicio

```java
private final Map<String, EstatusFulfillment> estatusPorJob = new ConcurrentHashMap<>();
private final Map<String, List<MiResult>> resultadosPorJob = new ConcurrentHashMap<>();

@Autowired
public MiServicio(WebClient.Builder builder, @Lazy MiServicio self) {  // ← el @Lazy es obligatorio
    this.self = self;
}

public EstatusFulfillment iniciarProceso(List<MiRow> filas) {
    String jobId = String.valueOf(System.currentTimeMillis());
    resultadosPorJob.put(jobId, new ArrayList<>());
    publicarEstatus(jobId, ESTATUS_EN_PROCESO, filas.size(), 0, 0, 0, null, LocalDateTime.now(), null);
    self.procesarJob(jobId, filas);       // ← self, NUNCA this
    return estatusPorJob.get(jobId);
}

@Async("fulfillmentExecutor")
public void procesarJob(String jobId, List<MiRow> filas) { ... }

public List<EstatusFulfillment> obtenerJobs() {
    return estatusPorJob.values().stream()
            .sorted(Comparator.comparing(EstatusFulfillment::getInicio).reversed())
            .toList();
}
```

### `@Async` no aplica en llamadas internas

Es el error que más cuesta encontrar, porque **no falla**: simplemente corre síncrono y el POST se
queda colgado hasta que termina toda la corrida. `this.procesarJob(...)` se salta el proxy de Spring
que implementa `@Async`. La solución es auto-inyectarse con `@Lazy` (el `@Lazy` rompe el ciclo de
dependencia consigo mismo) y llamar `self.procesarJob(...)`.

También en `docs/Hallazgos_Tecnicos.md`.

### Reusa lo que ya existe

- **`model/EstatusFulfillment`** tal cual. Sus campos dicen `totalTrackings` y `trackingActual`,
  pero una remisión **es** un tracking number: Availability lo reusa sin tocarlo. No crees otro
  modelo de estatus.
- **`AsyncConfig.fulfillmentExecutor`** — ya lo comparten dos módulos. No agregues otro bean de
  executor; el pool es de 1-2 hilos a propósito, para no saturar el gateway.
- Estatus válidos: `EN_PROCESO`, `COMPLETADO`, `COMPLETADO_CON_ERRORES`.

## Ritmo y errores de gateway

El patrón que pidió el usuario en su momento, y que conviene mantener:

1. **Pasada inicial**: la fila que falla por gateway (`500 Internal Server Error` o
   `504 Gateway Timeout`, por coincidencia de string sobre la respuesta) **no se reintenta en el
   momento**: se pausa, se difiere al final de la cola y se sigue avanzando con las demás.
2. **Rondas de reproceso**: al terminar la pasada, hasta `maxRondasReproceso` (3) vueltas sobre los
   diferidos. En la **última** ronda el resultado con error se conserva en vez de descartarse — si
   no, la fila desaparece del reporte y nadie se entera.
3. Entre filas normales, la pausa del SPEC (100 ms en Availability). La pausa por gateway es mayor
   (5 s en Availability, 10 s en Fulfillment).

Pausas y rondas van en `application.properties` con el prefijo del módulo, no como constantes.

## Lo que se resuelve **antes** de encolar

Si el job necesita una credencial, **pídela de forma síncrona antes de devolver el `jobId`**.
`AvailabilityService.iniciarReproceso` obtiene el `access_token` primero: así una contraseña
incorrecta falla en la misma respuesta HTTP, en vez de encolar un job que muere solo dos segundos
después y deja al usuario consultando un estatus que nunca avanza.

## Límites conocidos

- **El estado vive en memoria.** Un reinicio de la aplicación borra todos los jobs, sus estatus y
  sus resultados. Si alguna vez importa, hay que persistirlos en SQLite (`sqliteDataSource` es el
  único datasource escribible).
- **El mapa no se limpia.** Los jobs terminados se acumulan hasta el siguiente reinicio; `/jobs` los
  devuelve todos, ordenados por `inicio` descendente. Es deliberado: purgarlos rompería la descarga
  del `.xlsx` de un job viejo.

## Documentación

Los cuatro endpoints llevan `@Operation` / `@ApiResponse` / `@Parameter` y su subsección en
`docs/Documentacion_Endpoints.md` con los 4 puntos fijos, **en el mismo cambio** (CLAUDE.md
§Documentación). Para `/jobs`, que no recibe parámetros, la tabla se deja con una fila `Ninguno`.
