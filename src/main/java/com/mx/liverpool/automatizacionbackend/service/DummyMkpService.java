package com.mx.liverpool.automatizacionbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mx.liverpool.automatizacionbackend.model.AtgMarketplace;
import com.mx.liverpool.automatizacionbackend.model.DummyMkpResult;
import com.mx.liverpool.automatizacionbackend.model.DummyMkpRow;
import com.mx.liverpool.automatizacionbackend.model.EstatusFulfillment;
import com.mx.liverpool.automatizacionbackend.repository.RemisionRepository;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Log4j2
public class DummyMkpService {
    private static final String ESTATUS_EN_PROCESO = "EN_PROCESO";
    private static final String ESTATUS_COMPLETADO = "COMPLETADO";
    private static final String ESTATUS_COMPLETADO_CON_ERRORES = "COMPLETADO_CON_ERRORES";
    private static final String PLANTILLA = "dummy/plantilla-dummy.xlsx";
    private static final String RUTA_ORDENES = "/api/ordermanagement/orders/";
    private static final String RUTA_CANCELAR = "/api/ordermanagement/orders/{commercialId}/cancel";
    private static final Pattern ESPACIOS = Pattern.compile("[\\p{Z}\\p{Cc}]+");
    private static final Pattern INVISIBLES = Pattern.compile("\\p{Cf}");

    private final WebClient omsClient;
    private final ObjectMapper objectMapper;
    private final RemisionRepository remisionRepository;
    private final ExcelService excelService;
    private final DummyMkpService self;
    private final int tamanoLote;
    private final long pausaLoteMs;
    private final Path directorioSalida;
    private final List<String> encabezadoPlantilla;
    private final Map<String, String> filaPlantilla;
    private final Map<String, EstatusFulfillment> estatusPorJob = new ConcurrentHashMap<>();
    private final Map<String, List<DummyMkpResult>> resultadosPorJob = new ConcurrentHashMap<>();

    /** Fila dummy ya armada, con su JSON, en espera de envío y cancelación. */
    private record Pendiente(DummyMkpResult resultado, String json) {}

    @Autowired
    public DummyMkpService(WebClient.Builder webClientBuilder,
                           ObjectMapper objectMapper,
                           DataFormatter dataFormatter,
                           RemisionRepository remisionRepository,
                           ExcelService excelService,
                           @Lazy DummyMkpService self,
                           @Value("${dummy-mkp.oms.base-url}") String omsBaseUrl,
                           @Value("${dummy-mkp.oms.apikey}") String omsApikey,
                           @Value("${dummy-mkp.tamano-lote}") int tamanoLote,
                           @Value("${dummy-mkp.pausa-lote-ms}") long pausaLoteMs,
                           @Value("${dummy-mkp.directorio-salida}") String directorioSalida) {
        this.omsClient = webClientBuilder
                .baseUrl(omsBaseUrl)
                .defaultHeader("apikey", omsApikey)
                .build();
        this.objectMapper = objectMapper;
        this.remisionRepository = remisionRepository;
        this.excelService = excelService;
        this.self = self;
        this.tamanoLote = tamanoLote;
        this.pausaLoteMs = pausaLoteMs;
        this.directorioSalida = Path.of(directorioSalida);

        // Encabezado + fila 2 de la plantilla: los campos que el SPEC no reemplaza salen de aquí tal cual.
        try (InputStream is = new ClassPathResource(PLANTILLA).getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row encabezado = sheet.getRow(0);
            Row valores = sheet.getRow(1);
            List<String> columnas = new ArrayList<>();
            Map<String, String> fila = new LinkedHashMap<>();
            for (int i = 0; i < encabezado.getLastCellNum(); i++) {
                String columna = dataFormatter.formatCellValue(encabezado.getCell(i)).trim();
                if (columna.isEmpty()) continue;
                columnas.add(columna);
                fila.put(columna, dataFormatter.formatCellValue(valores.getCell(i)));
            }
            this.encabezadoPlantilla = List.copyOf(columnas);
            this.filaPlantilla = Collections.unmodifiableMap(fila);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la plantilla " + PLANTILLA, e);
        }
    }

    public EstatusFulfillment iniciarProceso(List<DummyMkpRow> filas) {
        log.info("Entrando a iniciarProceso de dummies con {} filas", filas.size());

        String jobId = String.valueOf(System.currentTimeMillis());
        // La carpeta se crea antes de encolar: si no hay permisos el usuario lo ve en la misma respuesta.
        try {
            Files.createDirectories(directorioSalida.resolve(jobId).resolve("jsons"));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta del job: " + e.getMessage());
        }

        resultadosPorJob.put(jobId, Collections.synchronizedList(new ArrayList<>()));
        publicarEstatus(jobId, ESTATUS_EN_PROCESO, filas.size(), 0, 0, null, LocalDateTime.now(), null);

        self.procesarJob(jobId, filas);

        log.info("Finalizando iniciarProceso de dummies, job {} encolado", jobId);
        return estatusPorJob.get(jobId);
    }

    // Hilo virtual por job: no se forma detrás de los jobs de fulfillment (ver Hallazgos [executors]).
    @Async("statusOmsExecutor")
    public void procesarJob(String jobId, List<DummyMkpRow> filas) {
        log.info("Entrando a procesarJob de dummies para job {} con {} filas", jobId, filas.size());

        verificarVigente(jobId);
        LocalDateTime inicio = estatusPorJob.get(jobId).getInicio();
        List<DummyMkpResult> resultados = resultadosPorJob.get(jobId);
        Path carpeta = directorioSalida.resolve(jobId);

        // Fase 1: consultar Bridgecore, armar filas dummy y sus JSON.
        List<Map<String, String>> filasDummy = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        int armadas = 0;
        for (DummyMkpRow fila : filas) {
            publicarEstatus(jobId, ESTATUS_EN_PROCESO, filas.size(), armadas, contarErrores(resultados),
                    "armando " + fila.getRemision(), inicio, null);
            armarFila(fila, carpeta, resultados, filasDummy, pendientes);
            armadas++;
        }

        try {
            excelService.escribirDummies(carpeta.resolve("dummies_" + jobId + ".xlsx"), encabezadoPlantilla, filasDummy);
        } catch (IOException e) {
            // Es solo de depuración: no detiene el envío.
            log.error("No se pudo escribir el Excel de dummies del job {}: {}", jobId, e.getMessage());
        }

        // Fase 2: enviar a Entrada Única y cancelar. Tras cada lote de envíos seguidos, pausa.
        int enviados = 0;
        for (Pendiente pendiente : pendientes) {
            DummyMkpResult resultado = pendiente.resultado();
            publicarEstatus(jobId, ESTATUS_EN_PROCESO, pendientes.size(), enviados, contarErrores(resultados),
                    "enviando " + resultado.getRemision(), inicio, null);
            enviarYCancelar(resultado, pendiente.json());
            enviados++;
            if (enviados % tamanoLote == 0 && enviados < pendientes.size()) {
                log.info("Job {}: {} envíos seguidos, pausa de {} ms", jobId, enviados, pausaLoteMs);
                pausar(pausaLoteMs);
            }
        }

        int conError = contarErrores(resultados);
        String estatusFinal = conError == 0 ? ESTATUS_COMPLETADO : ESTATUS_COMPLETADO_CON_ERRORES;
        publicarEstatus(jobId, estatusFinal, pendientes.size(), enviados, conError, null, inicio, LocalDateTime.now());
        log.info("Finalizando procesarJob de dummies para job {}: {} enviados, {} con error", jobId, enviados, conError);
    }

    public EstatusFulfillment obtenerEstatus(String jobId) {
        log.info("Entrando a obtenerEstatus de dummies para job {}", jobId);
        EstatusFulfillment estatus = estatusPorJob.get(jobId);
        if (estatus == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        log.info("Finalizando obtenerEstatus de dummies para job {}", jobId);
        return estatus;
    }

    public List<EstatusFulfillment> obtenerJobs() {
        log.info("Entrando a obtenerJobs de dummies");
        List<EstatusFulfillment> jobs = estatusPorJob.values().stream()
                .sorted(Comparator.comparing(EstatusFulfillment::getInicio).reversed())
                .toList();
        log.info("Finalizando obtenerJobs de dummies con {} jobs", jobs.size());
        return jobs;
    }

    public List<DummyMkpResult> obtenerResultados(String jobId) {
        log.info("Entrando a obtenerResultados de dummies para job {}", jobId);
        List<DummyMkpResult> resultados = resultadosPorJob.get(jobId);
        if (resultados == null) throw new IllegalArgumentException("No existe un job con id: " + jobId);
        List<DummyMkpResult> copia;
        synchronized (resultados) {
            copia = List.copyOf(resultados);
        }
        log.info("Finalizando obtenerResultados de dummies para job {} con {} resultados", jobId, copia.size());
        return copia;
    }

    public int liberarMemoria() {
        log.info("Entrando a liberarMemoria");
        int jobs = estatusPorJob.size();
        estatusPorJob.clear();
        resultadosPorJob.clear();
        log.info("Finalizando liberarMemoria con {} jobs liberados", jobs);
        return jobs;
    }

    private void armarFila(DummyMkpRow fila, Path carpeta, List<DummyMkpResult> resultados,
                           List<Map<String, String>> filasDummy, List<Pendiente> pendientes) {
        // Contra BRIDGECORE la remisión va tal cual (ver Hallazgos [remisión]); a OMS va rellenada a 10 dígitos.
        String remisionBc = fila.getRemision();
        String remision = rellenarDiezDigitos(remisionBc);
        String offerId = fila.getOfferId();

        if (offerId == null || offerId.isBlank()) {
            resultados.add(error(remision, offerId, "No aplica: falta offerId"));
            return;
        }

        List<AtgMarketplace> datos;
        try {
            datos = remisionRepository.obtenerDatosDummy(remisionBc);
        } catch (Exception e) {
            log.error("Error consultando Bridgecore para la remisión {}: {}", remision, e.getMessage());
            resultados.add(error(remision, offerId, "Error consultando Bridgecore: " + e.getMessage()));
            return;
        }
        if (datos.isEmpty()) {
            resultados.add(error(remision, offerId, "Sin datos en Bridgecore (id_cat_estatus=0, id_tipo_tx=1)"));
            return;
        }

        // Una fila dummy y un JSON por cada SKU de la remisión.
        for (AtgMarketplace dato : datos) {
            Map<String, String> filaDummy = armarFilaDummy(remision, offerId, dato);
            filasDummy.add(filaDummy);

            DummyMkpResult resultado = DummyMkpResult.builder()
                    .remision(remision)
                    .offerId(offerId)
                    .orderId(dato.getAtgOrderId())
                    .shippingGroupId(dato.getAtgShipGrpId())
                    .sku(dato.getSkuId())
                    .cantidad(dato.getCantidad())
                    .total(dato.getTotal())
                    .build();
            resultados.add(resultado);

            try {
                ObjectNode json = armarJson(filaDummy);
                Path archivo = carpeta.resolve("jsons").resolve(remision + "_" + dato.getSkuId() + ".json");
                Files.writeString(archivo, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                        StandardCharsets.UTF_8);
                resultado.setArchivoJson(archivo.toString());
                pendientes.add(new Pendiente(resultado, json.toString()));
            } catch (IOException e) {
                log.error("Error generando el JSON de la remisión {}: {}", remision, e.getMessage());
                resultado.setError("Error generando JSON: " + e.getMessage());
            }
        }
    }

    /** Copia la fila plantilla y reemplaza solo los campos que pide el SPEC. */
    Map<String, String> armarFilaDummy(String remision, String offerId, AtgMarketplace dato) {
        Map<String, String> fila = new LinkedHashMap<>(filaPlantilla);
        fila.put("ORDER_ID", dato.getAtgOrderId());
        fila.put("SHIPPING_GROUP_ID", dato.getAtgShipGrpId());
        fila.put("ID_CYBERSOURCE", dato.getIdCybersource());
        fila.put("SOMS_REMISSION", remision);
        fila.put("QUANTITY", dato.getCantidad());
        fila.put("BRIDGECORE_SKU_AMOUNT", dato.getTotal());
        fila.put("SKU", dato.getSkuId());
        fila.put("OFFERID", offerId);
        fila.put("OFFER_PRICE", dato.getTotal());
        return fila;
    }

    /** Misma estructura que transform_to_json del antiguo crea_json_vendor.py. */
    ObjectNode armarJson(Map<String, String> fila) {
        Map<String, String> r = new HashMap<>();
        fila.forEach((columna, valor) -> r.put(columna, limpiarTexto(valor)));

        ObjectNode raiz = objectMapper.createObjectNode();
        raiz.put("commercial_id", r.get("SOMS_REMISSION"));

        ObjectNode customer = raiz.putObject("customer");
        customer.put("firstname", r.get("FIRST_NAME"));
        customer.put("lastname", r.get("LAST_NAME"));
        ObjectNode billing = customer.putObject("billing_address");
        billing.put("firstname", r.get("DECODE(BILL.FIRST_NAME,NULL,SHIPP.FIRST_NAME,BILL.FIRST_NAME)"));
        billing.put("lastname", r.get("DECODE(BILL.LAST_NAME,NULL,SHIPP.LAST_NAME,BILL.LAST_NAME)"));
        billing.put("zip_code", r.get("DECODE(BILL.POSTAL_CODE,NULL,SHIPP.POSTAL_CODE,BILL.POSTAL_CODE)"));
        billing.put("city", r.get("DECODE(BILL.CITY,NULL,SHIPP.CITY,BILL.CITY)"));
        billing.put("country", r.get("COUNTRY"));
        billing.put("country_iso_code", r.get("ISO_CODE"));
        billing.put("state", r.get("DECODE(BILL.STATE_NAME,NULL,SHIPP.STATE_NAME,BILL.STATE_NAME)"));
        billing.put("phone", r.get("DECODE(BILL.HOME_PHONE,NULL,SHIPP.HOME_PHONE,BILL.HOME_PHONE)"));
        billing.put("locale", r.get("LOCALE"));
        billing.put("street_1", r.get("STREET_1"));
        billing.put("street_2", r.get("STREET_2"));
        customer.put("email", r.get("DECODE(USR.EMAIL,NULL,'MARKETPLACE@LIVERPOOL.COM.MX',USR.EMAIL)"));
        ObjectNode shipping = customer.putObject("shipping_address");
        shipping.put("firstname", r.get("FIRST_NAME_1"));
        shipping.put("lastname", r.get("LAST_NAME_1"));
        shipping.put("zip_code", r.get("POSTAL_CODE"));
        shipping.put("city", r.get("CITY"));
        shipping.put("country", r.get("COUNTRY_SH"));
        shipping.put("country_iso_code", r.get("COUNTRY_ISO_SH"));
        shipping.put("state", r.get("STATE_NAME"));
        shipping.put("phone", r.get("HOME_PHONE"));
        shipping.put("street_1", r.get("STREET_1SH"));
        shipping.put("street_2", r.get("STREET_2SH"));
        customer.put("customer_id", sinDecimalCero(r.get("PROFILE_ID")));

        ObjectNode offer = raiz.putArray("offers").addObject();
        ponerNumero(offer, "quantity", r.get("QUANTITY"));
        ponerNumero(offer, "price", r.get("BRIDGECORE_SKU_AMOUNT"));
        ponerNumero(offer, "shipping_price", r.get("SHIPPING_PRICE"));
        offer.put("shipping_type_code", r.get("SHIPPINGTYPE"));
        ArrayNode lineFields = offer.putArray("order_line_additional_fields");
        campo(lineFields, "liver-promo-label", "STRING").put("value", r.get("VALUE_PROMO"));
        ponerNumero(campo(lineFields, "offer-original-price", "NUMERIC"), "value", r.get("LIST_PRICE"));
        campo(lineFields, "product-sap-sku-id", "STRING").put("value", r.get("SKU"));
        lineFields.addObject().put("code", "reembolso-parcial").put("value", "false");
        campo(lineFields, "tipo-articulo", "STRING").put("value", r.get("PRODUCTTYPE"));
        offer.put("taxes", "");
        offer.put("currency_iso_code", r.get("CURR_ISO_CODE"));
        ponerNumero(offer, "leadtime_to_ship", r.get("LEADTIME"));
        offer.put("offer_id", sinDecimalCero(r.get("OFFERID")));
        ponerNumero(offer, "offer_price", r.get("OFFER_PRICE"));

        ObjectNode payment = raiz.putObject("payment_info");
        payment.put("payment_type", r.get("PAYMETNMETHOD"));
        payment.put("imprint_number", "");

        raiz.put("scored", true);
        raiz.put("shipping_zone_code", "Mexico");
        raiz.put("channel_code", "1");
        ArrayNode orderFields = raiz.putArray("order_additional_fields");
        orderFields.addObject().put("code", "reembolso-total").put("value", "false");
        orderFields.addObject().put("code", "calle-cliente").put("value", r.get("DECODE(SHIPP.STREET,NULL,'NA',SHIPP.STREET)"));
        orderFields.addObject().put("code", "num-ext-cliente").put("value", r.get("DECODE(SHIPP.EXTERIOR_NUMBER,NULL,'NA',SHIPP.EXTERIOR_NUMBER)"));
        orderFields.addObject().put("code", "num-int-cliente").put("value", "NA");
        orderFields.addObject().put("code", "edificio-cliente").put("value", "NA");
        orderFields.addObject().put("code", "entre-calles").put("value", "NA");
        orderFields.addObject().put("code", "colonia-cliente").put("value", "NA");
        orderFields.addObject().put("code", "delegacion-municipio-cliente").put("value", r.get("DECODE(SHIPP.MUNICIPALITY,NULL,'NA',SHIPP.MUNICIPALITY)"));
        orderFields.addObject().put("code", "mkp-numberof-trackid").put("value", "1");
        raiz.put("payment_workflow", "PAY_ON_ACCEPTANCE");
        return raiz;
    }

    /** Solo 2xx es éxito; si el envío falla no se cancela. Sin reintentos para no duplicar una orden creada. */
    private void enviarYCancelar(DummyMkpResult resultado, String json) {
        String remision = resultado.getRemision();
        try {
            resultado.setRespuestaEnvio(llamar(omsClient.post()
                    .uri(RUTA_ORDENES)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(json)));
        } catch (Exception e) {
            resultado.setError("Envío: " + describirError(remision, "envío", e));
            return;
        }

        try {
            resultado.setRespuestaCancelacion(llamar(omsClient.put().uri(RUTA_CANCELAR, remision)));
        } catch (Exception e) {
            resultado.setError("Cancelación: " + describirError(remision, "cancelación", e));
        }
    }

    private String llamar(WebClient.RequestHeadersSpec<?> peticion) {
        ResponseEntity<String> respuesta = peticion.retrieve().toEntity(String.class).block();
        if (respuesta == null) throw new IllegalStateException("Sin respuesta");
        String texto = respuesta.getStatusCode().value() + " " + Objects.toString(respuesta.getBody(), "");
        if (!respuesta.getStatusCode().is2xxSuccessful()) throw new IllegalStateException(texto);
        return texto;
    }

    private String describirError(String remision, String paso, Exception e) {
        String body = e instanceof WebClientResponseException w ? w.getResponseBodyAsString() : "";
        log.error("Error en {} de la remisión {}: {} - {}", paso, remision, e.getMessage(), body);
        return body.isEmpty() ? e.getMessage() : e.getMessage() + " - " + body;
    }

    private ObjectNode campo(ArrayNode campos, String code, String type) {
        return campos.addObject().put("code", code).put("type", type);
    }

    // Los numéricos del JSON viajan como número (igual que pandas); si el valor no es numérico se manda tal cual.
    private void ponerNumero(ObjectNode nodo, String campo, String valor) {
        try {
            nodo.put(campo, new BigDecimal(valor.trim()));
        } catch (RuntimeException e) {
            nodo.put(campo, valor);
        }
    }

    // NBSP y demás espacios Unicode → espacio normal (rachas colapsadas); invisibles (BOM, zero-width) y de control fuera.
    static String limpiarTexto(String valor) {
        if (valor == null) return null;
        String sinInvisibles = INVISIBLES.matcher(valor).replaceAll("");
        return ESPACIOS.matcher(sinInvisibles).replaceAll(" ").strip();
    }

    private String sinDecimalCero(String valor) {
        if (valor == null) return "";
        return valor.endsWith(".0") ? valor.substring(0, valor.length() - 2) : valor;
    }

    private DummyMkpResult error(String remision, String offerId, String mensaje) {
        return DummyMkpResult.builder().remision(remision).offerId(offerId).error(mensaje).build();
    }

    private int contarErrores(List<DummyMkpResult> resultados) {
        synchronized (resultados) {
            return (int) resultados.stream().filter(r -> r.getError() != null).count();
        }
    }

    private void pausar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Pausa interrumpida: {}", e.getMessage());
        }
    }

    // Tras /memoria/liberar el job ya no existe: se corta el hilo en vez de volver a escribirlo en memoria.
    private void verificarVigente(String jobId) {
        if (!resultadosPorJob.containsKey(jobId)) throw new CancellationException("Job " + jobId + " liberado de memoria");
    }

    private void publicarEstatus(String jobId, String estatus, int total, int procesados, int conError,
                                 String actual, LocalDateTime inicio, LocalDateTime fin) {
        verificarVigente(jobId);
        estatusPorJob.put(jobId, EstatusFulfillment.builder()
                .jobId(jobId)
                .estatus(estatus)
                .totalTrackings(total)
                .procesados(procesados)
                .conErrorGateway(conError)
                .reprocesados(0)
                .trackingActual(actual)
                .inicio(inicio)
                .fin(fin)
                .build());
    }

    private String rellenarDiezDigitos(String remision) {
        if (remision == null) return null;
        String limpio = remision.trim();
        return limpio.length() < 10 ? "0".repeat(10 - limpio.length()) + limpio : limpio;
    }
}
