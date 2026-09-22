package com.mx.liverpool.automatizacionbackend.controller;

import com.mx.liverpool.automatizacionbackend.service.ExcelService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoBillToService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoCombinadoService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoCompletoService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoEmailService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoF001Service;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoFirstNameService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoItemIdAutoService;
import com.mx.liverpool.automatizacionbackend.service.ReprocesoItemIdService;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/reproceso")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Reproceso", description = "Reenvío masivo de órdenes al servicio I200 (Apigee) a partir de un Excel")
public class ReprocesoController {
    private final ReprocesoItemIdService reprocesoItemIdService;
    private final ReprocesoItemIdAutoService reprocesoItemIdAutoService;
    private final ReprocesoF001Service reprocesoF001Service;
    private final ReprocesoBillToService reprocesoBillToService;
    private final ReprocesoCombinadoService reprocesoCombinadoService;
    private final ReprocesoEmailService reprocesoEmailService;
    private final ReprocesoFirstNameService reprocesoFirstNameService;
    private final ReprocesoCompletoService reprocesoCompletoService;
    private final ExcelService excelService;

    @Operation(summary = "Reprocesar órdenes reemplazando el ItemID contra I200",
            description = "Recibe un Excel de tres columnas (A: JSON del pedido, B: remisión, C: ItemID). Por cada fila " +
                    "reemplaza el ItemID del JSON con el valor de la columna C y envía la orden una por una al servicio I200 " +
                    "de Apigee, espaciando las llamadas (250 ms entre envíos y 1 s cada 25). Devuelve un .xlsx (descarga) con " +
                    "columnas: Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/itemid/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarItemId(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de tres columnas: A=JSON, B=remisión, C=ItemID") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_ItemId.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoItemIdService.reprocesar(
                                        excelService.leerReprocesoItemId(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes llenando el Item, el LinePriceInfo y el correo desde BRIDGECORE",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber). Por cada fila consulta " +
                    "en BRIDGECORE el detalle de SKUs de esa remisión, una consulta por remisión y con la remisión tal " +
                    "como viene en el archivo (no se rellenan ceros a la izquierda). Los SKUs se asignan por posición en " +
                    "el orden en que los devuelve BRIDGECORE: el primero al primer OrderLine, el segundo al segundo, etc., " +
                    "llenando ItemDesc, ItemID (siempre como texto), UnitPrice y ListPrice. Además escribe el " +
                    "CUSTOMER_EMAIL en toda llave EMailID vacía del JSON (a cualquier profundidad; un correo propio del " +
                    "pedido se respeta) y envía la orden al servicio I200 de Apigee, espaciando las llamadas (250 ms entre " +
                    "envíos y 1 s cada 25). No se envía cuando no hay detalle en BRIDGECORE, cuando el número de SKUs no " +
                    "coincide con el número de OrderLines (se reportan ambas cuentas y los datos de cada SKU quedan en las " +
                    "columnas Correo, SKU 1, SKU 2... para colocarlos a mano) o cuando algún EMailID queda vacío. Devuelve " +
                    "un .xlsx (descarga) con columnas: Request Original, TrackingNumber, Response y, cuando aplica, Correo " +
                    "más una columna por SKU.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/itemid-auto/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarItemIdAuto(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=TrackingNumber") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_ItemId_Auto.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoItemIdAutoService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes corrigiendo el Store contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber). Por cada fila, si algún " +
                    "OrderLine tiene \"Store\" o \"ShipNode\" con valor \"F001\" lo reemplaza por \"001\" y envía la orden una por una al servicio I200 " +
                    "de Apigee, espaciando las llamadas (250 ms entre envíos y 1 s cada 25). Las órdenes que no contienen F001 no se " +
                    "envían y se marcan como \"No F001\". Devuelve un .xlsx (descarga) con columnas: Request Original, " +
                    "TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/f001/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarF001(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=TrackingNumber") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_F001.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoF001Service.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes rellenando PersonInfoBillTo contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber). Por cada fila copia a " +
                    "\"PersonInfoBillTo\" los campos de \"PersonInfoShipTo\" que el BillTo trae vacíos (ausentes, nulos, vacíos " +
                    "o con solo espacios), respetando los que ya tienen valor propio y saltando las extensiones LExtn. Si tras " +
                    "el merge queda vacío algún campo obligatorio del INT200 la orden no se envía y se reporta cuál falta; los " +
                    "obligatorios con valor por omisión (Country, AddressLine3) se rellenan y sí se envían. Las órdenes que se " +
                    "envían van una por una al servicio I200 de Apigee, espaciando las llamadas (250 ms entre envíos y 1 s cada 25). " +
                    "Devuelve un .xlsx (descarga) con columnas: Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/billto/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarBillTo(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=TrackingNumber") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_BillTo.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoBillToService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes corrigiendo el Store y rellenando PersonInfoBillTo contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: TrackingNumber). Por cada fila aplica dos " +
                    "correcciones: (1) si algún OrderLine tiene \"Store\" o \"ShipNode\" con valor \"F001\" lo reemplaza por \"001\"; (2) copia a " +
                    "\"PersonInfoBillTo\" los campos de \"PersonInfoShipTo\" que el BillTo trae vacíos (ausentes, nulos, vacíos " +
                    "o con solo espacios), respetando los que ya tienen valor propio y saltando las extensiones LExtn. Si el " +
                    "pedido no trae \"PersonInfoShipTo\" (o viene vacío) no se envía. Si tras el merge queda vacío algún campo " +
                    "obligatorio del INT200 tampoco se envía y se reporta cuál falta; los obligatorios con valor por omisión " +
                    "(Country, AddressLine3) se rellenan y sí se envían. Las órdenes que no necesitan ninguna corrección también " +
                    "se envían. Los envíos van uno por uno al servicio I200 de Apigee, espaciando las llamadas (250 ms entre envíos " +
                    "y 1 s cada 25). Devuelve un .xlsx (descarga) con columnas: Request Original, TrackingNumber y Response; la " +
                    "columna Response indica las correcciones aplicadas: \"Enviado (F001, BillTo) | <respuesta>\", " +
                    "\"Enviado (sin cambios) | <respuesta>\", \"Falta: PersonInfoBillTo.State\", " +
                    "\"Falta el nodo PersonInfoShipTo, no hay de donde copiar\" o el error.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/combinado/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarCombinado(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=TrackingNumber") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_Combinado.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoCombinadoService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes rellenando solo el EMailID contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: remisión). Por cada fila busca en " +
                    "BRIDGECORE el CUSTOMER_EMAIL de esa remisión y lo escribe en toda llave \"EMailID\" del JSON que venga " +
                    "vacía, nula o con solo espacios, a cualquier profundidad; un correo propio del pedido se respeta y una " +
                    "llave EMailID que no exista no se crea. El resto del JSON no se toca (ItemID, precios y descripciones " +
                    "quedan igual). Envía la orden una por una al servicio I200 de Apigee, espaciando las llamadas (250 ms " +
                    "entre envíos y 1 s cada 25). La remisión se consulta tal como viene en la columna B, sin rellenar ceros, " +
                    "y se ata a la consulta como texto. No se envía cuando la remisión no tiene transacción en BRIDGECORE ni " +
                    "cuando algún EMailID queda vacío porque BRIDGECORE no trae correo. Devuelve un .xlsx (descarga) con " +
                    "columnas: Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/email/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarEmail(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=remisión") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_Email.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoEmailService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes rellenando EMailID y nombres contra I200",
            description = "Recibe un Excel de dos columnas (A: JSON del pedido, B: remisión). Por cada fila busca en " +
                    "BRIDGECORE el CUSTOMER_EMAIL y el NOMBRE_USUARIO de esa remisión y los escribe en las llaves " +
                    "\"EMailID\", \"FirstName\", \"MiddleName\" y \"LastName\" del JSON que vengan vacías, nulas o con " +
                    "solo espacios, a cualquier profundidad: alcanza PersonInfoShipTo y PersonInfoBillTo, y también " +
                    "PersonInfoContact si trae esas llaves. BRIDGECORE guarda el nombre completo en una sola columna, " +
                    "así que se reparte por posición: primera palabra al FirstName, última al LastName y todo lo de en " +
                    "medio junto al MiddleName. Un dato propio del pedido se respeta, una llave que no exista no se crea " +
                    "y el MiddleName nunca bloquea el envío por venir vacío (no es obligatorio en el INT200). El resto " +
                    "del JSON no se toca (ItemID, precios y descripciones quedan igual). Envía la orden una por una al " +
                    "servicio I200 de Apigee, espaciando las llamadas (250 ms entre envíos y 1 s cada 25). La remisión se " +
                    "consulta tal como viene en la columna B, sin rellenar ceros, y se ata a la consulta como texto. No se " +
                    "envía cuando la remisión no tiene transacción en BRIDGECORE ni cuando algún campo obligatorio queda " +
                    "vacío. Devuelve un .xlsx (descarga) con columnas: Request Original, TrackingNumber y Response.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/firstname/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarFirstName(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=remisión") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_FirstName.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoFirstNameService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }

    @Operation(summary = "Reprocesar órdenes aplicando todas las correcciones contra I200",
            description = "El reproceso completo: recibe un Excel de dos columnas (A: JSON del pedido, B: remisión) y por " +
                    "cada fila aplica, en este orden, las cuatro correcciones que los demás endpoints hacen por separado, " +
                    "quedándose solo con las que la orden necesita. 1) Store y ShipNode \"F001\" pasan a \"001\". " +
                    "2) ItemID, ItemDesc, UnitPrice y ListPrice se toman del detalle de SKU de la remisión en BRIDGECORE, " +
                    "asignados por posición y solo donde el pedido viene vacío (un precio en cero cuenta como vacío). " +
                    "3) EMailID, FirstName, MiddleName y LastName se toman de TX_CLIENTE; el nombre completo se reparte " +
                    "por posición (primera palabra al FirstName, última al LastName, lo de en medio al MiddleName). " +
                    "4) PersonInfoBillTo se rellena con lo que trae PersonInfoShipTo más los valores por default del " +
                    "INT200. El BillTo va al final para que herede el correo y el nombre que el paso 3 escribió en el " +
                    "ShipTo. En todos los pasos se respeta el dato propio del pedido: solo se llena lo vacío, nulo o con " +
                    "espacios, y una llave que no exista no se crea. Solo frenan el envío los campos que el INT200 exige " +
                    "(EMailID, FirstName, LastName y los obligatorios del BillTo); si BRIDGECORE no trae detalle de SKU o " +
                    "no cuadra con los OrderLines, ese paso se salta, se anota y la orden se envía igual. Una orden que no " +
                    "necesita nada también se envía, marcada \"sin cambios\". Las llamadas al I200 van espaciadas (250 ms " +
                    "entre envíos y 1 s cada 25) y la remisión se consulta tal como viene en la columna B. Devuelve un " +
                    ".xlsx (descarga) con columnas: Request Original, TrackingNumber y Response, donde Response indica qué " +
                    "se aplicó: \"Enviado (F001, Item, Correo, BillTo) | <respuesta>\", \"Enviado (sin cambios) | " +
                    "<respuesta>\", \"Enviado (F001, Correo | sin Item: ...) | <respuesta>\", \"No enviado. Sin datos en " +
                    "BRIDGECORE para: ...\", \"No enviado. Falta: PersonInfoBillTo.State\" o el error.")
    @ApiResponse(responseCode = "200", description = "Archivo .xlsx (descarga) con el resultado del reproceso")
    @PostMapping(value = "/completo/procesar", consumes = {"multipart/form-data"})
    public ResponseEntity<?> procesarCompleto(
            @Parameter(description = "Archivo Excel (.xlsx/.xls) de dos columnas: A=JSON, B=remisión") @RequestParam("file") MultipartFile file) throws IOException {
        if (excelService.esArchivoNoExcel(file.getOriginalFilename())) throw new IllegalArgumentException("Tipo de archivo inválido. Solo se permiten archivos Excel.");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reporte_Reproceso_Completo.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(
                        excelService.crearReporteReproceso(
                                reprocesoCompletoService.reprocesar(
                                        excelService.leerReprocesoNode(file)
                                )
                        )
                );
    }
}
