package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.constant.CancelacionAtgMkpConstant;
import com.mx.liverpool.automatizacionbackend.model.AtgMarketplace;
import com.mx.liverpool.automatizacionbackend.model.CorreoTx;
import com.mx.liverpool.automatizacionbackend.model.Dummy;
import com.mx.liverpool.automatizacionbackend.model.FulfillmentResult;
import com.mx.liverpool.automatizacionbackend.model.ComparativaTx;
import com.mx.liverpool.automatizacionbackend.model.OmsFaltante;
import com.mx.liverpool.automatizacionbackend.model.OrdenSoms;
import com.mx.liverpool.automatizacionbackend.model.TxDiffPorHora;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTAreaChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.STGrouping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Log4j2
public class ExcelService {
    private final DataFormatter dataFormatter;

    @Autowired
    public ExcelService(DataFormatter dataFormatter) {
        this.dataFormatter = dataFormatter;
    }

    public List<Map<Integer,String>> fromExcelToListOfRows(MultipartFile file, String numSheet, String numCol) {
        int[] habilitedCols = Arrays.stream(numCol.split(",")).mapToInt(Integer::parseInt).toArray();
        List<Map<Integer,String>> habilitedCells = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(Integer.parseInt(numSheet));

            for (Row row : sheet) {
                Map<Integer, String> cellData = new HashMap<>();
                for (int colIndex : habilitedCols) {
                    Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null) {
                        cellData.put(colIndex, dataFormatter.formatCellValue(cell));

                        if (colIndex == 7 || colIndex == 0) {
                            habilitedCells.add(cellData);
                            cellData = new HashMap<>();
                        }
                    }
                }

                if (habilitedCells.isEmpty()) {
                    throw new IllegalArgumentException("La fila " + row.getRowNum() + " no tiene datos en columnas habilitadas.");
                }
            }

            return habilitedCells;
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar el archivo Excel: " + e.getMessage());
        }
    }

    public byte[] crearReporteVerificarEnOMSOrdenVenta(Map<String, Map<String, Object>> result) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("OrdenesVenta");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Order No");
            header.createCell(1).setCellValue("Status en OMS");
            header.createCell(2).setCellValue("Response Body");
            header.createCell(3).setCellValue("OrderStatuses");

            for (Map.Entry<String, Map<String, Object>> entry : result.entrySet()) {
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                row.createCell(0).setCellValue(entry.getKey());
                Map<String, Object> data = entry.getValue();
                row.createCell(1).setCellValue((Integer) data.get("status"));

                String content = (String) data.get("responseBody");
                if (content.length() > 32767) content = content.substring(0, 32764) + "...";
                row.createCell(2).setCellValue(content);

                row.createCell(3).setCellValue(truncarCelda((String) data.get("orderStatuses")));
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static final String[] ENCABEZADO_FALTANTES_OMS = {
            "atg_order_id", "atg_ship_grp_id", "Status en OMS", "Response Body", "OrderStatuses",
            "FECHA_TX_COMPRA", "Diferencia", "error_detail", "id_tipo_tx", "orden_venta",
            "id", "id_cat_estatus", "pedido", "boleta", "terminal", "remision",
            "total_cobrado", "total_original", "is_mkp", "zip_code", "recognition_store",
            "recognition_store_channel", "recognition_store_sub_channel", "tienda_cliente"
    };

    public byte[] crearReporteFaltantes(List<OmsFaltante> filas,
                                        Map<String, Map<String, Object>> omsResult,
                                        Function<OmsFaltante, String> llave) throws IOException {
        log.info("Entrando a crearReporteFaltantes con {} filas", filas.size());
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Faltantes");

            Row header = sheet.createRow(0);
            for (int i = 0; i < ENCABEZADO_FALTANTES_OMS.length; i++) {
                header.createCell(i).setCellValue(ENCABEZADO_FALTANTES_OMS[i]);
            }

            int rowNum = 1;
            for (OmsFaltante fila : filas) {
                Row row = sheet.createRow(rowNum++);
                Map<String, Object> oms = omsResult.get(llave.apply(fila));

                int col = 0;
                escribirCelda(row, col++, fila.getAtgOrderId());
                escribirCelda(row, col++, fila.getAtgShipGrpId());
                escribirCelda(row, col++, oms == null ? "" : oms.get("status"));
                escribirCelda(row, col++, oms == null ? "" : oms.get("responseBody"));
                escribirCelda(row, col++, oms == null ? "" : oms.get("orderStatuses"));
                escribirCelda(row, col++, fila.getFechaTxCompra());
                escribirCelda(row, col++, fila.getDiferencia());
                escribirCelda(row, col++, fila.getErrorDetail());
                escribirCelda(row, col++, fila.getIdTipoTx());
                escribirCelda(row, col++, fila.getOrdenVenta());
                escribirCelda(row, col++, fila.getId());
                escribirCelda(row, col++, fila.getIdCatEstatus());
                escribirCelda(row, col++, fila.getPedido());
                escribirCelda(row, col++, fila.getBoleta());
                escribirCelda(row, col++, fila.getTerminal());
                escribirCelda(row, col++, fila.getRemision());
                escribirCelda(row, col++, fila.getTotalCobrado());
                escribirCelda(row, col++, fila.getTotalOriginal());
                escribirCelda(row, col++, fila.getIsMkp());
                escribirCelda(row, col++, fila.getZipCode());
                escribirCelda(row, col++, fila.getRecognitionStore());
                escribirCelda(row, col++, fila.getRecognitionStoreChannel());
                escribirCelda(row, col++, fila.getRecognitionStoreSubChannel());
                escribirCelda(row, col, fila.getTiendaCliente());
            }

            workbook.write(out);
            log.info("Finalizando crearReporteFaltantes");
            return out.toByteArray();
        }
    }

    public byte[] crearReporteFulfillment(List<FulfillmentResult> resultados) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Fulfillment");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("TrackingNumber");
            header.createCell(1).setCellValue("Response");
            header.createCell(2).setCellValue("JSON");

            for (FulfillmentResult resultado : resultados) {
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                row.createCell(0).setCellValue(resultado.getTrackingNumber());
                row.createCell(1).setCellValue(truncarCelda(resultado.getResponse()));
                row.createCell(2).setCellValue(truncarCelda(resultado.getJson()));
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public List<String> leerRemisionesDeExcel(MultipartFile file) {
        log.info("Entrando a leerRemisionesDeExcel");
        List<String> remisiones = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                Cell cell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) continue;
                String valor = dataFormatter.formatCellValue(cell).trim();
                if (!valor.isEmpty()) remisiones.add(valor);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error al leer el Excel de remisiones: " + e.getMessage());
        }

        log.info("Finalizando leerRemisionesDeExcel con {} remisiones", remisiones.size());
        return remisiones;
    }

    public byte[] crearReporteOrdenSoms(List<OrdenSoms> resultados) throws IOException {
        log.info("Entrando a crearReporteOrdenSoms con {} resultados", resultados.size());
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Ordenes");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Remision");
            header.createCell(1).setCellValue("Status Datos");
            header.createCell(2).setCellValue("Status SOMS");
            header.createCell(3).setCellValue("Nodo Destinatario");
            header.createCell(4).setCellValue("Response");

            int rowNum = 1;
            for (OrdenSoms resultado : resultados) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(truncarCelda(resultado.getRemision()));
                row.createCell(1).setCellValue(truncarCelda(resultado.getStatusDatos()));
                row.createCell(2).setCellValue(truncarCelda(resultado.getStatusSoms()));
                row.createCell(3).setCellValue(truncarCelda(resultado.getNodoDestinatario()));
                row.createCell(4).setCellValue(truncarCelda(resultado.getResponse()));
            }

            workbook.write(out);
            log.info("Finalizando crearReporteOrdenSoms");
            return out.toByteArray();
        }
    }

    public byte[] crearReporteTxDiff(List<ComparativaTx> comparativas) throws IOException {
        log.info("Entrando a crearReporteTxDiff con {} comparativas", comparativas.size());
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (ComparativaTx comparativa : comparativas) {
                crearHojaComparativa(workbook, comparativa.getNombreHoja(), comparativa.getTitulo(),
                        comparativa.getAyer(), comparativa.getHoy());
            }

            workbook.write(out);
            log.info("Finalizando crearReporteTxDiff");
            return out.toByteArray();
        }
    }

    private void crearHojaComparativa(XSSFWorkbook workbook, String nombreHoja, String titulo,
                                      List<TxDiffPorHora> ayer, List<TxDiffPorHora> hoy) {
        XSSFSheet sheet = workbook.createSheet(nombreHoja);

        // Fusionar horas de ayer y hoy: [0]=ayer, [1]=hoy. TreeMap asegura orden cronológico (HH:MM).
        TreeMap<String, int[]> porHora = new TreeMap<>();
        for (TxDiffPorHora fila : ayer) {
            porHora.computeIfAbsent(fila.getHora(), h -> new int[2])[0] = fila.getTotal() == null ? 0 : fila.getTotal();
        }
        for (TxDiffPorHora fila : hoy) {
            porHora.computeIfAbsent(fila.getHora(), h -> new int[2])[1] = fila.getTotal() == null ? 0 : fila.getTotal();
        }

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Hora");
        header.createCell(1).setCellValue("Ayer");
        header.createCell(2).setCellValue("Hoy");

        int rowNum = 1;
        for (Map.Entry<String, int[]> entry : porHora.entrySet()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(truncarCelda(entry.getKey()));
            row.createCell(1).setCellValue(entry.getValue()[0]);
            row.createCell(2).setCellValue(entry.getValue()[1]);
        }
        int ultimaFila = rowNum - 1;

        if (ultimaFila < 1) {
            log.warn("Sin datos para la hoja {}, se omite la gráfica", nombreHoja);
            return;
        }

        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 4, 1, 20, 28);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(titulo);
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis catAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        catAxis.setTitle("Hora");
        XDDFValueAxis valAxis = chart.createValueAxis(AxisPosition.LEFT);
        valAxis.setTitle("Transacciones");
        valAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> horas = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(1, ultimaFila, 0, 0));
        XDDFNumericalDataSource<Double> serieAyer = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(1, ultimaFila, 1, 1));
        XDDFNumericalDataSource<Double> serieHoy = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(1, ultimaFila, 2, 2));

        XDDFAreaChartData data = (XDDFAreaChartData) chart.createData(ChartTypes.AREA, catAxis, valAxis);
        data.setVaryColors(true);
        XDDFAreaChartData.Series sAyer = (XDDFAreaChartData.Series) data.addSeries(horas, serieAyer);
        sAyer.setTitle("Ayer", null);
        XDDFAreaChartData.Series sHoy = (XDDFAreaChartData.Series) data.addSeries(horas, serieHoy);
        sHoy.setTitle("Hoy", null);
        chart.plot(data);

        // Convertir a áreas apiladas (stacked)
        CTAreaChart ctAreaChart = chart.getCTChart().getPlotArea().getAreaChartArray(0);
        ctAreaChart.addNewGrouping().setVal(STGrouping.STACKED);
    }

    private String truncarCelda(String content) {
        if (content == null) return "";
        if (content.length() > 32767) return content.substring(0, 32764) + "...";
        return content;
    }

    private static final int MAX_FILAS_POR_HOJA = 1_048_576;
    private static final String[] ENCABEZADO_CORREOS_TX = {
            "error_detail", "id_tipo_tx", "atg_ship_grp_id", "atg_order_id", "orden_venta",
            "customer_email", "id", "id_cat_estatus", "pedido", "fecha_tx_compra", "boleta",
            "terminal", "remision", "orden_venta", "total_cobrado", "total_original",
            "atg_order_id", "atg_ship_grp_id", "is_mkp", "zip_code", "recognition_store",
            "recognition_store_channel", "recognition_store_sub_channel", "tienda_cliente"
    };

    public LinkedHashMap<String, List<String>> leerCorreosPorSegmento(MultipartFile zip) {
        log.info("Entrando a leerCorreosPorSegmento");
        LinkedHashMap<String, List<String>> segmentos = new LinkedHashMap<>();

        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".xlsx")) continue;
                String nombre = nombreBaseSegmento(entry.getName());
                log.info("Leyendo correos del segmento {}", nombre);
                List<String> correos = new ArrayList<>(new LinkedHashSet<>(leerCorreosDeHoja(copiarEntrada(zis))));
                segmentos.put(nombre, correos);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error al leer el ZIP de correos: " + e.getMessage());
        }

        log.info("Finalizando leerCorreosPorSegmento con {} segmentos", segmentos.size());
        return segmentos;
    }

    private String nombreBaseSegmento(String entryName) {
        String archivo = entryName.replace('\\', '/');
        archivo = archivo.substring(archivo.lastIndexOf('/') + 1);
        int punto = archivo.lastIndexOf('.');
        return punto > 0 ? archivo.substring(0, punto) : archivo;
    }

    private InputStream copiarEntrada(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = zis.read(buffer)) > 0) baos.write(buffer, 0, len);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private List<String> leerCorreosDeHoja(InputStream is) throws IOException {
        List<String> correos = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // omitir título "Correo"
                Cell cell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) continue;
                String correo = dataFormatter.formatCellValue(cell).trim();
                if (!correo.isEmpty()) correos.add(correo);
            }
        }
        return correos;
    }

    public void escribirReporteCorreosTx(List<CorreoTx> resultados, Path destino) throws IOException {
        log.info("Entrando a escribirReporteCorreosTx con {} registros en {}", resultados.size(), destino.getFileName());
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             OutputStream out = Files.newOutputStream(destino)) {

            int numHoja = 1;
            Sheet sheet = workbook.createSheet("Resultado_" + numHoja);
            crearEncabezadoCorreosTx(sheet);
            int rowNum = 1;

            for (CorreoTx tx : resultados) {
                if (rowNum >= MAX_FILAS_POR_HOJA) {
                    numHoja++;
                    sheet = workbook.createSheet("Resultado_" + numHoja);
                    crearEncabezadoCorreosTx(sheet);
                    rowNum = 1;
                }
                escribirFilaCorreoTx(sheet.createRow(rowNum++), tx);
            }

            workbook.write(out);
            workbook.dispose();
            log.info("Finalizando escribirReporteCorreosTx en {} hoja(s) para {}", numHoja, destino.getFileName());
        }
    }

    private void crearEncabezadoCorreosTx(Sheet sheet) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < ENCABEZADO_CORREOS_TX.length; i++) {
            header.createCell(i).setCellValue(ENCABEZADO_CORREOS_TX[i]);
        }
    }

    private void escribirFilaCorreoTx(Row row, CorreoTx tx) {
        int col = 0;
        escribirCelda(row, col++, tx.getErrorDetail());
        escribirCelda(row, col++, tx.getIdTipoTx());
        escribirCelda(row, col++, tx.getAtgShipGrpId());
        escribirCelda(row, col++, tx.getAtgOrderId());
        escribirCelda(row, col++, tx.getOrdenVenta());
        escribirCelda(row, col++, tx.getCustomerEmail());
        escribirCelda(row, col++, tx.getId());
        escribirCelda(row, col++, tx.getIdCatEstatus());
        escribirCelda(row, col++, tx.getPedido());
        escribirCelda(row, col++, tx.getFechaTxCompra());
        escribirCelda(row, col++, tx.getBoleta());
        escribirCelda(row, col++, tx.getTerminal());
        escribirCelda(row, col++, tx.getRemision());
        escribirCelda(row, col++, tx.getOrdenVenta());
        escribirCelda(row, col++, tx.getTotalCobrado());
        escribirCelda(row, col++, tx.getTotalOriginal());
        escribirCelda(row, col++, tx.getAtgOrderId());
        escribirCelda(row, col++, tx.getAtgShipGrpId());
        escribirCelda(row, col++, tx.getIsMkp());
        escribirCelda(row, col++, tx.getZipCode());
        escribirCelda(row, col++, tx.getRecognitionStore());
        escribirCelda(row, col++, tx.getRecognitionStoreChannel());
        escribirCelda(row, col++, tx.getRecognitionStoreSubChannel());
        escribirCelda(row, col, tx.getTiendaCliente());
    }

    private void escribirCelda(Row row, int columna, Object valor) {
        row.createCell(columna).setCellValue(truncarCelda(valor == null ? "" : valor.toString()));
    }

    public String crearReporteCancelacion(List<Dummy> dummies, List<AtgMarketplace> atgMarketplaces) {
        String excelId = "";
        for (Dummy dummy : dummies) {
            AtgMarketplace marketplace = atgMarketplaces.stream()
                    .filter(m -> m.getRemision().equals(dummy.getRemision()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No se encontraron datos de Bridgecore para la remisión: " + dummy.getRemision()));
        }

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Cancelaciones");

        Row headerRow = sheet.createRow(0);
        int cellIndex = 0;
        for (CancelacionAtgMkpConstant constant : CancelacionAtgMkpConstant.values()) {
            if (CancelacionAtgMkpConstant.DECODE == constant) {
                headerRow.createCell(9).setCellValue(constant.getValues()[0]); cellIndex++;
                headerRow.createCell(10).setCellValue(constant.getValues()[2]); cellIndex++;
                headerRow.createCell(11).setCellValue(constant.getValues()[4]); cellIndex++;
                headerRow.createCell(12).setCellValue(constant.getValues()[6]); cellIndex++;

                headerRow.createCell(15).setCellValue(constant.getValues()[8]);
                headerRow.createCell(16).setCellValue(constant.getValues()[10]);
                headerRow.createCell(21).setCellValue(constant.getValues()[12]);

                headerRow.createCell(70).setCellValue(constant.getValues()[14]);
                headerRow.createCell(71).setCellValue(constant.getValues()[16]);
                headerRow.createCell(72).setCellValue(constant.getValues()[18]);

                headerRow.createCell(75).setCellValue(constant.getValues()[20]);
                headerRow.createCell(76).setCellValue(constant.getValues()[22]);
                headerRow.createCell(77).setCellValue(constant.getValues()[24]);
                continue;
            }

            if (cellIndex == 15) {
                cellIndex++; cellIndex++;
                continue;
            }

            if (cellIndex == 21) {
                cellIndex++;
                continue;
            }

            if (cellIndex == 70 || cellIndex == 75) {
                cellIndex++; cellIndex++; cellIndex++;
                continue;
            }

            headerRow.createCell(cellIndex).setCellValue(constant.name()); cellIndex++;
        }

        int rowNum = 1;
        for (CancelacionAtgMkpConstant constant : CancelacionAtgMkpConstant.values()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(constant.name());

            break;
            // String valor = String.join(" | ", constant.getValues());
            // row.createCell(1).setCellValue(valor);
        }

        String generadorId = String.valueOf(System.currentTimeMillis());
        String fileName = "ReporteCancelacion_" + generadorId + ".xlsx";
        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            workbook.write(fileOut);
            excelId = fileName;
            log.info("Excel creado exitosamente con el nombre: {}", fileName);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return excelId;
    }
}
