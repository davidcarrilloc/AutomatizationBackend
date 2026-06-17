---
name: graficos-excel
description: Úsala cuando haya que agregar o crear gráficas/charts/visualizaciones (barras, líneas, pastel, dispersión) dentro de un reporte .xlsx en este backend Java — por ejemplo sobre los reportes SOMS, TX o fulfillment generados en ExcelService con Apache POI. Cubre la decisión POI vs Python, dónde colocar el código, una receta POI 5.2.3 completa y las convenciones del repo.
---

# Creación de gráficos en Excel (Apache POI)

Guía para generar reportes `.xlsx` con **gráficas nativas y editables** en este backend
(`com.mx.liverpool.automatizacionbackend`). El proyecto ya trae `poi-ooxml` **5.2.3** en el
`pom.xml`, que soporta gráficas de barras, líneas, pastel y dispersión vía `XSSFChart` / `XDDF`.

## 1. Decisión: POI nativo vs Python

- **Usa POI nativo (Java)** para barras / líneas / pastel / dispersión sobre datos tabulares.
  Genera gráficas reales y editables dentro del `.xlsx` (no imágenes), sin runtime extra.
- **NO uses `ExecutePythonService` para esto.** Ese servicio invoca un módulo Python fijo por
  nombre, depende de un path hardcodeado, escribe/lee archivos en disco y hace
  `System.setProperty("user.dir", ...)` — muta estado global del proceso y no es seguro con los
  virtual threads del proyecto. Meter graficación ahí acopla el deploy a Python sin beneficio.
- Solo considera otra vía (p. ej. imagen tipo matplotlib incrustada) si necesitas una
  visualización muy custom tipo dashboard que POI no pueda expresar. Para lo común, POI sobra.

## 2. Dónde va el código

Sigue el flujo que ya existe para reportes Excel:

- **Genera el `byte[]`** en un método nuevo de
  `src/main/java/com/mx/liverpool/automatizacionbackend/service/ExcelService.java`
  (mira `crearReporteOrdenSoms` como molde).
- **Expón la descarga** desde el controller con `Content-Disposition: attachment`,
  igual que `SOMSController.consultarOrdenes`:

  ```java
  HttpHeaders headers = new HttpHeaders();
  headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Con_Grafica.xlsx");
  return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(excelService.crearReporteConGrafica(datos));
  ```

## 3. Receta POI 5.2.3 (gráfica de barras)

Patrón: escribe primero una **tabla resumen** (categorías + valores) en un rango de celdas, y
luego apunta la gráfica a ese rango. La gráfica se enlaza a celdas, no a valores sueltos.

```java
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public byte[] crearReporteConGrafica(Map<String, Integer> conteos) throws IOException {
    log.info("Entrando a crearReporteConGrafica con {} categorias", conteos.size());
    try (XSSFWorkbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

        XSSFSheet sheet = workbook.createSheet("Resumen");

        // 1) Tabla resumen: col 0 = categoria, col 1 = valor
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Categoria");
        header.createCell(1).setCellValue("Total");

        int rowNum = 1;
        for (Map.Entry<String, Integer> e : conteos.entrySet()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(e.getKey());
            row.createCell(1).setCellValue(e.getValue());
        }
        int ultimaFila = rowNum - 1; // índice 0-based de la última fila con datos

        // 2) Lienzo y posición de la gráfica (col4,fila1 -> col12,fila16)
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 4, 1, 12, 16);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Distribución por categoría");
        chart.setTitleOverlay(false);

        // 3) Leyenda y ejes
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);
        XDDFCategoryAxis catAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis valAxis = chart.createValueAxis(AxisPosition.LEFT);
        valAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        // 4) Fuentes de datos = rangos de celdas (filas 1..ultimaFila)
        XDDFDataSource<String> categorias = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(1, ultimaFila, 0, 0));
        XDDFNumericalDataSource<Double> valores = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(1, ultimaFila, 1, 1));

        // 5) Construir y plotear
        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, catAxis, valAxis);
        data.setBarDirection(BarDirection.COL);
        XDDFChartData.Series serie = data.addSeries(categorias, valores);
        serie.setTitle("Total", null);
        chart.plot(data);

        workbook.write(out);
        log.info("Finalizando crearReporteConGrafica");
        return out.toByteArray();
    }
}
```

### Variante: gráfica de pastel (sin ejes)

```java
XDDFChart chart = drawing.createChart(anchor);
chart.setTitleText("Proporción por categoría");
chart.getOrAddLegend().setPosition(LegendPosition.RIGHT);

XDDFDataSource<String> categorias = XDDFDataSourcesFactory.fromStringCellRange(
        sheet, new CellRangeAddress(1, ultimaFila, 0, 0));
XDDFNumericalDataSource<Double> valores = XDDFDataSourcesFactory.fromNumericCellRange(
        sheet, new CellRangeAddress(1, ultimaFila, 1, 1));

XDDFPieChartData data = (XDDFPieChartData) chart.createData(ChartTypes.PIE, null, null);
data.setVaryColors(true);
data.addSeries(categorias, valores);
chart.plot(data);
```

Para líneas, usa `ChartTypes.LINE` con `catAxis` + `valAxis` (igual que barras pero
`XDDFLineChartData`).

## 4. Convenciones del repo (obligatorias)

Respeta `CLAUDE.md`:

- Servicio con `@Service` + `@RequiredArgsConstructor` + `@Log4j2`.
- Loguea entrada/salida de cada método público: `log.info("Entrando a X")` /
  `log.info("Finalizando X")`, siempre con placeholders `{}` (nunca concatenación).
- Nombres de método en español: `crearReporte...ConGrafica`, `crearGrafica...`.
- Para celdas de texto largo (p. ej. columna `Response`), reusa el helper privado existente
  `truncarCelda(...)` de `ExcelService` — respeta el límite de Excel de 32 767 chars por celda.
- Controller delgado: arma headers y delega; deja que `ControllerAdvice` maneje errores.

## 5. Gotchas / límites

- **Usa `XSSFWorkbook`, no `SXSSFWorkbook`** para hojas con gráficas: el modo streaming
  (`SXSSF`) no soporta dibujos/charts. Si el reporte es enorme, separa: datos crudos en una
  hoja `SXSSF` y resumen+gráfica en un libro `XSSF`, o solo resumen con gráfica.
- La gráfica se enlaza al **rango de celdas**, así que esas celdas deben existir y contener los
  valores antes de plotear; si el rango queda vacío la gráfica sale en blanco.
- Las gráficas se renderizan al abrir en **Excel/LibreOffice**; algunos visores ligeros o
  preliminares no las dibujan.
- POI no maneja bien gráficas combinadas (barras + líneas en un mismo plot); si lo necesitas,
  evalúa dos gráficas separadas.
- Paquetes clave: `org.apache.poi.xddf.usermodel.chart.*` y `org.apache.poi.xssf.usermodel.*`.
