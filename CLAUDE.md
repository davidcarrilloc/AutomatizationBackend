# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build WAR (skip tests)
mvn clean package -DskipTests

# Run locally
java -jar target/AutomatizacionBackend-1.jar

# Docker (full stack with MySQL)
docker-compose up --build
```

Server runs on port **9091**. Swagger UI at `http://localhost:9091/swagger-ui/index.html`.

There are no automated tests in the project — manual testing via Swagger or Postman is the norm.

## Architecture

Spring Boot 3.5 WAR app (Java 21, virtual threads enabled) targeting Liverpool's internal e-commerce systems.

**Package layout:** `com.mx.liverpool.automatizacionbackend`
- `configuration/` — datasource beans, security, WebSocket broker
- `controller/` — REST endpoints (thin, delegates to services)
- `service/` — business logic; some services call Oracle directly, others wrap `WebClient`
- `repository/` — Spring JDBC `NamedParameterJdbcTemplate` queries (no JPA)
- `scheduler/` — cron jobs (TxScheduler, NoOMSScheduler)
- `websocket/` — STOMP message broker for real-time TX metrics
- `payload/` — request/response DTOs
- `model/` — data models and row mappers

## Data Sources (4 simultaneous)

All SQL queries are **externalized in `src/main/resources/queries.properties`** and injected via `@Value`. Never write inline SQL — add it to that file.

| Bean qualifier | Database | Purpose |
|---|---|---|
| `bridgeCoreDataSource` | Oracle `APPSPRO` (172.17.212.224) | Main e-commerce transactions |
| `bridgeCoreQa2DataSource` | Oracle `APPSQ` (172.16.212.22) | QA environment |
| `atgCoreDataSource` | Oracle `atgpro` (172.17.212.7) | ATG catalog / digital codes |
| `sqliteDataSource` | Local `VyE.db` file | Transaction metrics cache |

Inject the right template using `@Qualifier`. SQLite is the only writable local store; Oracle connections are read-only from this app's perspective.

## Key Integrations

- **COPOMEX API** — postal code / address lookup; `CopomexClient` (WebClient)
- **Liverpool OMS (Apigee)** — order verification; API key in `application.properties`
- **Pushover** — push notifications for anomaly alerts (`PushoverService`)
- **Apache POI** — generates `.xlsx` reports for marketplace reprocessing flows
- **Python subprocess** — `PythonService` shells out to a local Python install at a hardcoded path (`application.properties: python.path`) for remission processing

## Schedulers

| Class | Schedule | What it does |
|---|---|---|
| `TxScheduler` | Every 60 s + every 30 s | Pulls TX metrics into SQLite; broadcasts via WebSocket; fires Pushover alert if volume is abnormal |
| `NoOMSScheduler` | Daily 08:00 & 16:00 | Detects orders not sent to OMS, generates Excel report, sends email/push notification |

## WebSocket

STOMP endpoint `/ws-tx`. Clients subscribe to `/topic/tx` for real-time TX metrics. The server pushes updates from `TxScheduler`; clients can also send to `/app/enviarDetalle` with a date range.

## Security

HTTP Basic Auth + Form Login via Spring Security. All endpoints require authentication. CSRF is disabled. Credentials are in `application.properties` (not environment variables in the default profile).

---

## Coding Conventions

Follow these conventions exactly when creating new files or modifying existing ones.

### Controllers

```java
@RestController
@RequestMapping("/api/v1/nombre-recurso")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
public class NombreController {

    private final NombreService nombreService;

    @PostMapping("/accion")
    public ResponseEntity<?> accionNombre(@RequestBody @Valid RequestPayload request) {
        return ResponseEntity.ok(nombreService.accion(request));
    }

    @GetMapping("/accion")
    public ResponseEntity<?> obtenerNombre(@RequestParam String param) {
        return ResponseEntity.ok(nombreService.obtener(param));
    }
}
```

- Always `@RestController` + `@RequestMapping` + `@CrossOrigin(origins = "*")` + `@RequiredArgsConstructor` + `@Log4j2`
- Always return `ResponseEntity<?>` — never a plain POJO or primitive
- Use `ResponseEntity.ok(body)` for success; let `@RestControllerAdvice` handle errors
- Method names in Spanish: `verificarOrden`, `obtenerDetalle`, `crearRecurso`
- Thin controllers — no business logic, just delegate to the service

### Services

```java
@Service
@RequiredArgsConstructor
@Log4j2
public class NombreService {

    private final NombreRepository nombreRepository;

    public ResultType obtenerNombre(String param) {
        log.info("Entrando a obtenerNombre");
        // logic
        log.info("Finalizando obtenerNombre");
        return result;
    }
}
```

- Always `@Service` + `@RequiredArgsConstructor` + `@Log4j2`
- Log entry and exit of every public method: `log.info("Entrando a X")` / `log.info("Finalizando X")`
- Use `{}` placeholders in log calls — never String concatenation
- Method verbs in Spanish: `obtener`, `crear`, `insertar`, `notificar`, `ejecutar`, `buscar`
- Private helpers are allowed for internal logic

### Repositories

```java
@Repository
@Log4j2
public class NombreRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String queryNombre;

    @Autowired
    public NombreRepository(
            @Qualifier("bridgeCoreDataSource") NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${consulta.nombre}") String queryNombre) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryNombre = queryNombre;
    }

    public List<NombreModel> obtenerNombre(String param) {
        Map<String, Object> params = new HashMap<>();
        params.put("param", param);
        return jdbcTemplate.query(queryNombre, params, new BeanPropertyRowMapper<>(NombreModel.class));
    }
}
```

- Use explicit `@Autowired` constructor (not `@RequiredArgsConstructor`) to apply `@Qualifier` and `@Value`
- Always use `NamedParameterJdbcTemplate` — never `JdbcTemplate`
- Always inject SQL via `@Value("${consulta.clave}")` — never inline SQL
- Add new queries to `src/main/resources/queries.properties` with kebab-case key: `consulta.nombre-operacion`
- Build parameter maps with `HashMap` + `.put()`
- Row mapping with `new BeanPropertyRowMapper<>(Model.class)`
- Method names start with `obtener` — repositories are read-only unless writing to SQLite

### Models

```java
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class NombreModel {
    private String campo;
    private Integer otroCampo;
}
```

- Always `@Data` + `@NoArgsConstructor` + `@Builder` + `@AllArgsConstructor`
- No business logic — pure data containers for DB row mapping
- Field names in camelCase matching the DB column names (BeanPropertyRowMapper handles snake_case → camelCase)

### Payloads (Request/Response DTOs)

```java
// Request
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NombreRequest {
    @NotBlank(message = "El campo es requerido")
    @JsonProperty("Campo")
    private String campo;
}

// Response
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class NombreResponse {
    @JsonProperty("Campo")
    private String campo;
}
```

- Request DTOs: `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor` + Jakarta validation (`@NotBlank`, `@Email`, etc.)
- Response DTOs: `@Data` + `@NoArgsConstructor` + `@Builder` + `@AllArgsConstructor`
- Use `@JsonProperty("PascalCase")` when the JSON field name differs from the Java field name
- Validation messages in Spanish

### Custom Exceptions

```java
public class NombreException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public NombreException(String message) {
        super(message);
    }
}
```

- Always extend `RuntimeException`
- Always include `@Serial` + `serialVersionUID = 1L`
- Single constructor accepting `String message`
- Register a handler in `ControllerAdvice.java`

### Exception Handler (ControllerAdvice)

```java
@ExceptionHandler(NombreException.class)
public ResponseEntity<?> handleNombreException(NombreException ex) {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)  // pick the appropriate status
        .body(ex.getMessage());
}
```

- All handlers live in `ControllerAdvice.java` (`@RestControllerAdvice`)
- HTTP status mapping: 400 BAD_REQUEST, 404 NOT_FOUND, 500 INTERNAL_SERVER_ERROR, 502 BAD_GATEWAY, 503 SERVICE_UNAVAILABLE

### Schedulers

```java
@Component
@RequiredArgsConstructor
@Log4j2
public class NombreScheduler {

    private final NombreService nombreService;

    @Scheduled(cron = "0 * * * * *")
    public void executeTask() {
        log.info("Ejecutando NombreScheduler");
        // logic
    }
}
```

- `@Component` + `@RequiredArgsConstructor` + `@Log4j2`
- Public methods named `executeTask()` or `executeNotify()`
- Decorated with `@Scheduled(cron = "...")`

### WebClient (External API calls)

```java
@Service
@Log4j2
public class NombreClient {

    private final WebClient webClient;

    @Autowired
    public NombreClient(WebClient.Builder webClientBuilder,
                        @Value("${api.base-url}") String baseUrl,
                        @Value("${api.key}") String apiKey) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", apiKey)
            .build();
    }

    public NombreResponse llamar(String param) {
        return webClient.post()
            .uri("/endpoint")
            .bodyValue(param)
            .retrieve()
            .bodyToMono(NombreResponse.class)
            .onErrorResume(e -> { log.error("Error: {}", e.getMessage()); return Mono.empty(); })
            .block();
    }
}
```

- Build `WebClient` in constructor via `WebClient.Builder`
- Use `.block()` to convert reactive → synchronous
- Handle errors with `.onErrorResume()`

### General Rules

- **Lombok over boilerplate** — always use `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Log4j2` etc.
- **Constructor injection** — use `@RequiredArgsConstructor` + `final` fields; use explicit `@Autowired` constructor only when `@Qualifier`/`@Value` are needed
- **No inline SQL** — every query goes in `queries.properties`
- **Spanish naming** — method names, log messages, and validation messages in Spanish
- **Logging** — `@Log4j2` always; log entry/exit on public methods; `{}` placeholders
- **Streams** — use `.stream()`, `.filter()`, `.map()`, `.toList()` for collections; use `.getFirst()` / `.getLast()`
- **Date/time** — use `LocalDateTime`; manipulate with `.minusDays()`, `.withHour()`, etc.
- **Immutable maps/lists** — use `Map.of()` and `List.of()` for static data; `HashMap`/`ArrayList` for mutable
