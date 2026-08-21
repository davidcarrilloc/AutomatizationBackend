> Estado: **Implementado** → `POST /api/v1/fulfillment-txt` (+ estatus, jobs y excel por `jobId`)

Haz un nuevo endpoint dentro de FulfillmentController que consumna la case siguiente de java y que no haga timeouts, que mande todas de golpe:




import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;

public class OrderFulfillmentApp {

    private static final String ENDPOINT_URL = "https://ogcp-apigke-site-d.liverpool.com.mx/order-service/v1/order/fulFillment";

    public static void main(String[] args) {
        String archivoEntrada = "d:\\trackings.txt"; 
        String archivoSalida = "d:\\resultados_status.txt";

        HttpClient client = HttpClient.newHttpClient();

        System.out.println("Iniciando procesamiento...");

        try (Stream<String> lineas = Files.lines(Paths.get(archivoEntrada));
             BufferedWriter writer = new BufferedWriter(new FileWriter(archivoSalida, false))) {
            
            writer.write("TRACKING_NUMBER,STATUS_OMS");
            writer.newLine();

            lineas.map(String::trim)
                  .filter(linea -> !linea.isEmpty())
                  .forEach(trackingNumber -> {
                      procesarTracking(client, trackingNumber, writer);
                  });

            System.out.println("Procesamiento terminado. Resultados guardados en: " + archivoSalida);

        } catch (IOException e) {
            System.err.println("Error general de Archivos: " + e.getMessage());
        }
    }

    private static void procesarTracking(HttpClient client, String trackingNumber, BufferedWriter writer) {
        String jsonBody = String.format("{\n" +
                "    \"processes\":[\n" +
                "        {\"trackingNumber\":\"%s\"}\n" +
                "    ]\n" +
                "}", trackingNumber);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            String statusOms = extraerStatusOms(responseBody);

            String lineaResultado = String.format("%s,%s", trackingNumber, statusOms);
            
            writer.write(lineaResultado);
            writer.newLine();
            writer.flush();

            System.out.println("[Procesado] -> " + lineaResultado);

        } catch (IOException | InterruptedException e) {
            System.err.printf("Error con el tracking %s: %s%n", trackingNumber, e.getMessage());
            try {
                writer.write(trackingNumber + ",ERROR_PETICION");
                writer.newLine();
            } catch (IOException ignored) {}
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Método auxiliar para extraer "statusOms" sin librerías JSON externas
     */
    private static String extraerStatusOms(String json) {
        if (json == null || !json.contains("\"statusOms\"")) {
            return "UNKNOWN";
        }
        try {
            int index = json.indexOf("\"statusOms\"");
            String sub = json.substring(index);
            int inicioValor = sub.indexOf(":") + 1;
            String valorConComillas = sub.substring(inicioValor).trim();
            

            if (valorConComillas.startsWith("\"")) {
                int finComillas = valorConComillas.indexOf("\"", 1);
                return valorConComillas.substring(1, finComillas);
            } else if (valorConComillas.startsWith("null")) {
                return "null";
            }
        } catch (Exception e) {
            return "PARSE_ERROR";
        }
        return "UNKNOWN";
    }
}

---

## Decisiones

La primera versión fue síncrona (`POST /api/v1/fulfillment/txt`, 50 llamadas concurrentes, el
`.xlsx` en la misma respuesta). Se rehízo como módulo propio con `jobId`: la espera no cabía en una
petición HTTP y las 50 simultáneas eran justo lo que castigaba al gateway. **La concurrencia se movió
de "N trackings a la vez dentro de un job" a "N jobs a la vez, cada uno tranquilo por dentro".**

| Punto | Decisión |
|---|---|
| Controller | `FulfillmentTxtController` en `/api/v1/fulfillment-txt`, los 4 endpoints del patrón de job |
| Entrada | `.txt`, un trackingnumber por línea (como la clase original) |
| Salida | `.xlsx` de 2 columnas: TrackingNumber, StatusOms, por `jobId` |
| Respuesta | `202 Accepted` con el `jobId`; el avance se consulta en `/estatus/{jobId}` |
| Ritmo dentro del job | Uno por uno, sin pausa entre llamadas |
| Jobs simultáneos | Sin tope: un hilo virtual por job (`statusOmsExecutor`) |
| Reintentos | Se mantienen sin pausas: el que falla se encola al final, `status-oms.max-rondas-reproceso=3` |
| Trackings | Se rellenan a 10 dígitos, igual que `/reproceso` |
| Celda sin valor | Etiquetas de la clase original: `UNKNOWN`, `null`, `PARSE_ERROR`, `ERROR_PETICION` |

## Cierre

**Componentes:**
- `controller/FulfillmentTxtController.java` — los 4 endpoints: `POST /`, `/estatus/{jobId}`, `/jobs`, `/excel/{jobId}`
- `service/StatusOmsService.java` — el job completo: `iniciarProceso`, `procesarJob`, `obtenerEstatus`, `obtenerJobs`, `obtenerResultados`
- `configuration/AsyncConfig.java` — bean `statusOmsExecutor` (hilos virtuales)
- `model/StatusOmsResult.java` — fila del reporte (trackingNumber, statusOms)
- `model/EstatusFulfillment.java` — reusado tal cual para el estatus del job
- `service/ExcelService.java` — `leerLineasDeTxt`, `esArchivoNoTxt`, `crearReporteStatusOms`
- `application.properties` — `status-oms.max-rondas-reproceso=3`

**Hallazgos:**
1. **La mitad de la clase original ya estaba en el repo.** `FulfillmentResponse` ya trae `statusOms`
   mapeado por Jackson: el `extraerStatusOms` a base de `indexOf`/`substring` y el `HttpClient`
   propio se cayeron completos.
2. **Jackson no distingue la llave ausente de la llave en null**, y la clase original sí: `UNKNOWN`
   cuando no viene `statusOms`, `"null"` cuando viene nula. Se conserva con un
   `json.contains("\"statusOms\"")` antes de parsear. Es la única razón por la que ese `contains`
   existe; quitarlo cambia la salida sin que nada truene.
3. **Solo se reintenta lo que falló como petición.** `esErrorGateway` difiere únicamente
   `ERROR_PETICION`; `UNKNOWN`, `null` y `PARSE_ERROR` son respuestas que el gateway sí dio y
   reintentarlas es quemar llamadas para obtener lo mismo.
4. **El tope de rondas no es opcional.** Sin pausas, un `while (!diferidos.isEmpty())` con el gateway
   caído gira indefinidamente. Por eso `status-oms.max-rondas-reproceso`, y por eso en la última
   ronda el error se conserva en el reporte en vez de descartarse: si se descarta, la fila desaparece
   del `.xlsx` y nadie se entera.
5. **`fulfillmentExecutor` no se tocó.** Sigue en 1-2 hilos para `/reproceso`. El módulo nuevo tiene
   su propio executor de hilos virtuales; compartirlo habría hecho que los jobs de statusOms
   esperaran turno detrás de un reproceso.
6. **`StatusOmsService` copia `llamarFulfillment` y `rellenarDiezDigitos`** (~20 líneas) en vez de
   inyectar `FulfillmentService`. Decisión explícita: los dos módulos quedan sueltos y `/reproceso`
   no se toca. Si un tercer módulo llega a pegarle al mismo endpoint, ahí sí vale extraer un cliente.
7. **El lector de `.txt` vive público en `ExcelService`** (`leerLineasDeTxt`, `esArchivoNoTxt`).
   `SOMSService` tiene su propia copia privada del mismo cuerpo; no se tocó, pero es la limpieza
   obvia el día que alguien pase por ahí.

**Verificación:**
- `mvn clean package -DskipTests` con JDK 23 → BUILD SUCCESS.
- 17 aserciones sobre `StatusOmsService`, todas verdes: 10 de `extraerStatusOms` (valor, llave nula,
  llave ausente, lista vacía, JSON nulo, respuesta de error del gateway, JSON truncado, objeto en vez
  de lista, varios elementos) y 7 de `esErrorGateway` (solo `ERROR_PETICION` se difiere).
- **Pendiente:** prueba por Swagger. Subir dos `.txt` seguidos y confirmar que `GET /jobs` muestra los
  dos en `EN_PROCESO` a la vez y que cada `/excel/{jobId}` baja su propio `.xlsx`. Arrancar la
  aplicación abre los 4 datasources Oracle que no responden desde la máquina de desarrollo.