---
name: api-externa
description: Úsala cuando el SPEC pida consultar un servicio externo desde este backend — Apigee, OGCP, commercetools, COPOMEX, SOMS — o cuando una llamada con WebClient falle con DataBufferLimitException, 401 de token, o tumbe la corrida completa en vez de dejar el error en la celda del reporte. Cubre el molde de WebClient, OAuth client_credentials, el límite de 256 KB del codec y dónde va (y dónde no) un secret.
---

# Llamar un API externa

Todas las integraciones de este backend usan `WebClient` en modo síncrono (`.block()`), nunca
`RestTemplate`. Moldes vivos: `AvailabilityService` (dos clientes + OAuth), `FulfillmentService`
(el más simple), `CopomexClient`, `ReprocesoService`.

## Molde

```java
@Autowired
public MiClient(WebClient.Builder builder,
                @Value("${mi.base-url}") String baseUrl,
                @Value("${mi.apikey}") String apiKey) {
    this.webClient = builder
            .baseUrl(baseUrl)
            .defaultHeader("apikey", apiKey)
            .build();
}

public MiResponse consultar(String id) {
    return webClient.get()
            .uri("/ruta/{id}", id)
            .retrieve()
            .bodyToMono(MiResponse.class)
            .onErrorResume(e -> { log.error("Error consultando {}: {}", id, e.getMessage()); return Mono.empty(); })
            .block();
}
```

- El cliente se arma **en el constructor** con `WebClient.Builder` inyectado, no se instancia a mano.
- `.block()` para volverlo síncrono: el resto del proyecto es imperativo y mezclar reactivo con
  bucles `for` solo confunde.
- **Un fallo de una fila no puede tumbar la corrida.** El error se atrapa y termina como texto en la
  celda del reporte: `"error": "<mensaje>"`. Así el usuario ve qué falló y qué no, en el mismo Excel.
  Para diagnosticar sirve el cuerpo de la respuesta, no solo el mensaje:

  ```java
  String body = e instanceof WebClientResponseException w ? w.getResponseBodyAsString() : "";
  log.error("Error en la remisión {}: {} - {}", remision, e.getMessage(), body);
  ```

## El límite de 256 KB del codec

`WebClient` viene con un tope **por defecto de 256 KB** de respuesta en memoria. Cualquier respuesta
más grande —una orden de commercetools con `expand`, un pedido con muchas líneas— falla con
`DataBufferLimitException: Exceeded limit on max bytes to buffer`. Es un error que se manifiesta en
producción con los pedidos grandes y nunca en las pruebas con los chicos.

Se sube **por cliente**, nunca global:

```java
this.ctClient = builder
        .baseUrl(ctBaseUrl)
        .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
        .build();
```

## OAuth `client_credentials`

Dos pasos, dos esquemas de autenticación distintos (molde: `AvailabilityService.obtenerToken`):

1. **Token** — `POST` a la URL de auth con `Basic base64(clientId:secret)`:

   ```java
   webClient.post()
       .uri(URI.create(tokenUrl))                       // URI absoluta: sobrescribe el baseUrl
       .headers(h -> h.setBasicAuth(clientId, password))
       .retrieve().bodyToMono(TokenResponse.class).block();
   ```

   `URI.create(...)` deja usar el mismo cliente para el host de auth y el de datos: no hace falta un
   tercer `WebClient`.

2. **Llamadas** — `Bearer` en cada una:

   ```java
   .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
   ```

`payload/response/TokenResponse` ya existe (`access_token`, `expires_in`, con
`@JsonIgnoreProperties(ignoreUnknown = true)`). Reúsalo.

**Un token por corrida.** Se pide una vez al iniciar el job y vive en memoria mientras dura; no se
cachea entre jobs ni se persiste.

## Secrets

Dos reglas distintas según lo que haya decidido el usuario en el SPEC:

- **En `application.properties`**: las apikeys de Apigee, el token de COPOMEX, el `client-id` de
  commercetools. Son las que ya están ahí y ahí se quedan.
- **Por parámetro de la petición**: cuando el usuario lo pide explícitamente (el *secret* del API
  client de commercetools). Entonces **no se guarda en ningún archivo del repo, no se persiste y no
  se escribe en bitácora** — ni en un `log.debug`. Documéntalo en el `@Parameter` de Swagger:

  > "Secret del API client de commercetools con el que se genera el access_token. No se almacena ni
  > se registra en bitácora."

Si el SPEC no lo dice, **pregunta**: es la clase de decisión que no se asume.

## Antes de escribir la URL

Las URLs base, apikeys, `project-key` y ritmos van en `application.properties` con el prefijo del
módulo (`availability.*`, `reproceso.i200.*`, `copomex.*`). En el código solo el path relativo.
Nunca pegues una URL completa en el SPEC ni en el servicio.
