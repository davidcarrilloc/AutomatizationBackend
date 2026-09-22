package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ValidacionSlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/validacion-sl")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Validación SL", description = "Resultado de la validación de las remisiones enviadas por SFTP: qué existen, cuáles no y el acumulado histórico")
public class ValidacionSlController {

    private static final int TAMANO_PAGINA = 200;

    private final ValidacionSlService validacionSlService;

    @Operation(summary = "Resumen y serie de los últimos 30 días",
            description = "Devuelve la fecha del último snapshot descargado, el conteo de los tres archivos " +
                    "(existen, noexisten, historico) y la serie diaria de los últimos 30 días guardada en SQLite. " +
                    "Es lo que consumen las dos gráficas de la página.")
    @ApiResponse(responseCode = "200", description = "JSON con fecha, conteos y serie")
    @GetMapping("/resumen")
    public ResponseEntity<?> obtenerResumen() {
        return ResponseEntity.ok(validacionSlService.obtenerResumen());
    }

    @Operation(summary = "Bloque de filas de un archivo",
            description = "Devuelve un fragmento HTML de hasta 200 filas para htmx, más el botón que pide el " +
                    "siguiente bloque. Cuando ya no hay más filas devuelve el fragmento sin botón.")
    @ApiResponse(responseCode = "200", description = "Fragmento HTML con las filas de la tabla")
    @GetMapping(value = "/filas", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> obtenerFilas(
            @Parameter(description = "Archivo a listar", example = "existen")
            @RequestParam String archivo,
            @Parameter(description = "Número de fila desde la que continúa el listado", example = "0")
            @RequestParam(defaultValue = "0") int desde) {
        List<String> filas = validacionSlService.obtenerFilas(archivo, desde, TAMANO_PAGINA);
        return ResponseEntity.ok(armarFilas(archivo, desde, filas));
    }

    @Operation(summary = "Buscar una remisión en los tres archivos",
            description = "Recorre existen.csv, noexisten.csv y existen_h.csv y devuelve un fragmento HTML " +
                    "indicando en cuáles de los tres aparece la remisión.")
    @ApiResponse(responseCode = "200", description = "Fragmento HTML con el resultado de la búsqueda")
    @GetMapping(value = "/buscar", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> buscarRemision(
            @Parameter(description = "Remisión o shipping group a buscar", example = "1234567890")
            @RequestParam String remision) {
        if (remision.isBlank()) {
            return ResponseEntity.ok("");
        }
        return ResponseEntity.ok(armarBusqueda(remision, validacionSlService.buscar(remision)));
    }

    @Operation(summary = "Descargar el CSV completo",
            description = "Devuelve el archivo .csv tal cual llegó del SFTP, como descarga.")
    @ApiResponse(responseCode = "200", description = "Archivo .csv")
    @GetMapping("/descargar")
    public ResponseEntity<?> descargarArchivo(
            @Parameter(description = "Archivo a descargar", example = "existen")
            @RequestParam String archivo) {
        byte[] contenido = validacionSlService.descargar(archivo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + validacionSlService.nombreArchivo(archivo) + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(contenido);
    }

    @Operation(summary = "Volver a leer los archivos del SFTP",
            description = "Fuerza la descarga de los tres archivos fuera del horario del scheduler y actualiza el " +
                    "conteo del día. No requiere parámetros.")
    @ApiResponse(responseCode = "200", description = "JSON con fecha, conteos y serie ya actualizados")
    @PostMapping("/refrescar")
    public ResponseEntity<?> refrescarArchivos() {
        return ResponseEntity.ok(validacionSlService.descargarYGuardar());
    }

    private String armarFilas(String archivo, int desde, List<String> filas) {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < filas.size(); i++) {
            html.append("<tr><td>").append(desde + i + 1).append("</td><td>")
                    .append(escapar(filas.get(i))).append("</td></tr>");
        }
        if (filas.size() == TAMANO_PAGINA) {
            html.append("<tr id=\"mas-").append(escapar(archivo)).append("\"><td colspan=\"2\">")
                    .append("<button class=\"mas\" hx-get=\"api/v1/validacion-sl/filas?archivo=")
                    .append(escapar(archivo)).append("&desde=").append(desde + filas.size())
                    .append("\" hx-target=\"#mas-").append(escapar(archivo))
                    .append("\" hx-swap=\"outerHTML\">Cargar más</button></td></tr>");
        } else if (filas.isEmpty() && desde == 0) {
            html.append("<tr><td colspan=\"2\" class=\"vacio\">Sin registros.</td></tr>");
        }
        return html.toString();
    }

    private String armarBusqueda(String remision, List<String> encontrado) {
        if (encontrado.isEmpty()) {
            return "<span class=\"no\">" + escapar(remision) + " no aparece en ninguno de los tres archivos.</span>";
        }
        return "<span class=\"si\">" + escapar(remision) + " está en: " + escapar(String.join(", ", encontrado)) + ".</span>";
    }

    private String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
