package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.OrdenSoms;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Log4j2
public class OrdenSomsService {
    private static final String DATOS = "DATOS";
    private static final String SIN_DATOS = "SIN DATOS";
    private static final long PAUSA_ENTRE_CONSULTAS_MS = 2000L;
    private static final long PAUSA_CADA_LOTE_MS = 10000L;
    private static final int TAMANO_LOTE = 10;

    private final WebClient webClient;

    @Autowired
    public OrdenSomsService(WebClient.Builder webClientBuilder,
                            @Value("${soms.getorden.base-url}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public List<OrdenSoms> consultarOrdenes(List<String> remisiones, int muestra) {
        log.info("Entrando a consultarOrdenes con {} remisiones", remisiones.size());
        List<String> seleccionadas = seleccionarMuestraAleatoria(remisiones, muestra);
        log.info("Se recibieron {} remisiones, consultando una muestra de {}", remisiones.size(), seleccionadas.size());
        List<OrdenSoms> resultados = new ArrayList<>();
        int contador = 0;
        for (String remision : seleccionadas) {
            resultados.add(consultarOrden(remision));
            contador++;
            if (contador < seleccionadas.size()) {
                pausar(contador % TAMANO_LOTE == 0 ? PAUSA_CADA_LOTE_MS : PAUSA_ENTRE_CONSULTAS_MS);
            }
        }
        log.info("Finalizando consultarOrdenes con {} resultados", resultados.size());
        return resultados;
    }

    private List<String> seleccionarMuestraAleatoria(List<String> remisiones, int muestra) {
        if (muestra <= 0 || remisiones.size() <= muestra) {
            return remisiones;
        }
        List<String> copia = new ArrayList<>(remisiones);
        Collections.shuffle(copia);
        return new ArrayList<>(copia.subList(0, muestra));
    }

    private void pausar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Pausa entre consultas interrumpida: {}", e.getMessage());
        }
    }

    private OrdenSoms consultarOrden(String remision) {
        log.info("Consultando orden para remisión {}", remision);
        try {
            String xml = webClient.post()
                    .uri("/wsoms/getOrden/")
                    .contentType(MediaType.TEXT_XML)
                    .bodyValue(construirEnvelope(remision))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parsearRespuesta(remision, xml);
        } catch (WebClientResponseException e) {
            log.error("Error HTTP consultando remisión {}: {}", remision, e.getMessage());
            return filaError(remision, e.getMessage(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error consultando remisión {}: {}", remision, e.getMessage());
            return filaError(remision, e.getMessage(), "");
        }
    }

    private String construirEnvelope(String remision) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:smow="http://www.SMOWS02P.SMWS201C.Request.com">
                <soapenv:Header/>
                <soapenv:Body>
                <smow:SMOWS02POperation>
                <smow:qry_orden_req>
                <smow:req_orden>%s</smow:req_orden>
                </smow:qry_orden_req>
                </smow:SMOWS02POperation>
                </soapenv:Body>
                </soapenv:Envelope>""".formatted(remision);
    }

    private OrdenSoms parsearRespuesta(String remision, String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        String nombre = obtenerTexto(doc, "nombre");
        String calle = obtenerTexto(doc, "calle");
        String codpost = obtenerTexto(doc, "codpost");
        boolean tieneDatos = !nombre.isBlank() && !calle.isBlank() && !codpost.isBlank();

        String estatus = obtenerTexto(doc, "estatus");

        NodeList destinatarios = doc.getElementsByTagName("destinatario");
        String nodoDestinatario = destinatarios.getLength() > 0
                ? nodoComoTexto(destinatarios.item(0))
                : SIN_DATOS;

        return OrdenSoms.builder()
                .remision(remision)
                .statusDatos(tieneDatos ? DATOS : SIN_DATOS)
                .statusSoms(estatus)
                .nodoDestinatario(nodoDestinatario)
                .response(xml)
                .build();
    }

    private OrdenSoms filaError(String remision, String mensaje, String response) {
        return OrdenSoms.builder()
                .remision(remision)
                .statusDatos("\"error\": \"" + (mensaje == null ? "" : mensaje) + "\"")
                .statusSoms(SIN_DATOS)
                .nodoDestinatario(SIN_DATOS)
                .response(response == null ? "" : response)
                .build();
    }

    private String obtenerTexto(Document doc, String tag) {
        NodeList nodos = doc.getElementsByTagName(tag);
        if (nodos.getLength() == 0) return "";
        String texto = nodos.item(0).getTextContent();
        return texto == null ? "" : texto.trim();
    }

    private String nodoComoTexto(Node nodo) {
        try {
            StringWriter writer = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(nodo), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("Error serializando nodo destinatario: {}", e.getMessage());
            return SIN_DATOS;
        }
    }
}
