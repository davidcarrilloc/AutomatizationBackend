package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.exception.TxDiffException;
import com.mx.liverpool.automatizacionbackend.exception.TxNotFound;
import com.mx.liverpool.automatizacionbackend.model.CobroRow;
import com.mx.liverpool.automatizacionbackend.model.ComparativaTx;
import com.mx.liverpool.automatizacionbackend.model.TxDiffPorHora;
import com.mx.liverpool.automatizacionbackend.model.TxPorMinuto;
import com.mx.liverpool.automatizacionbackend.payload.response.CobroResponse;
import com.mx.liverpool.automatizacionbackend.payload.response.ItemsResponse;
import com.mx.liverpool.automatizacionbackend.repository.TxQA2Repository;
import com.mx.liverpool.automatizacionbackend.repository.TxRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class TxService {
    private static final List<Integer> CANALES_APP = List.of(9);
    private static final List<Integer> CANALES_WEB = List.of(1, 8);
    private static final List<Integer> TIPOS_TODOS = List.of(0, 1);
    private static final List<Integer> TIPOS_SL = List.of(1);
    private static final List<Integer> TIPOS_BT = List.of(0);

    private final TxRepository txRepository;
    private final TxQA2Repository txQA2Repository;
    private final ExcelService excelService;

    @Autowired
    public TxService(TxRepository txRepository, TxQA2Repository txQA2Repository, ExcelService excelService) {
        this.txRepository = txRepository;
        this.txQA2Repository = txQA2Repository;
        this.excelService = excelService;
    }

    public byte[] obtenerDiferenciaTxHoyvsAyer() {
        log.info("Entrando a obtenerDiferenciaTxHoyvsAyer");
        try {
            LocalDateTime ahora = LocalDateTime.now();
            LocalDateTime hoyInicio = ahora.toLocalDate().atStartOfDay();
            LocalDateTime ayerInicio = hoyInicio.minusDays(1);
            LocalDateTime ayerFin = ahora.minusDays(1);

            List<ComparativaTx> comparativas = List.of(
                    construirComparativa("APP", "APP", CANALES_APP, TIPOS_TODOS, ayerInicio, ayerFin, hoyInicio, ahora),
                    construirComparativa("WEB", "WEB", CANALES_WEB, TIPOS_TODOS, ayerInicio, ayerFin, hoyInicio, ahora),
                    construirComparativa("APP SL", "APP SL", CANALES_APP, TIPOS_SL, ayerInicio, ayerFin, hoyInicio, ahora),
                    construirComparativa("WEB SL", "WEB SL", CANALES_WEB, TIPOS_SL, ayerInicio, ayerFin, hoyInicio, ahora),
                    construirComparativa("APP BT", "APP BT", CANALES_APP, TIPOS_BT, ayerInicio, ayerFin, hoyInicio, ahora),
                    construirComparativa("WEB BT", "WEB BT", CANALES_WEB, TIPOS_BT, ayerInicio, ayerFin, hoyInicio, ahora)
            );

            byte[] reporte = excelService.crearReporteTxDiff(comparativas);
            log.info("Finalizando obtenerDiferenciaTxHoyvsAyer");
            return reporte;
        } catch (Exception e) {
            log.error("Error al generar el reporte de diferencia TX: {}", e.getMessage());
            throw new TxDiffException("Error al generar el reporte de diferencia TX: " + e.getMessage());
        }
    }

    private ComparativaTx construirComparativa(String nombreHoja, String segmento,
                                               List<Integer> canales, List<Integer> tiposArticulo,
                                               LocalDateTime ayerInicio, LocalDateTime ayerFin,
                                               LocalDateTime hoyInicio, LocalDateTime hoyFin) {
        List<TxDiffPorHora> ayer = txRepository.obtenerTxDiff(canales, tiposArticulo, ayerInicio, ayerFin);
        List<TxDiffPorHora> hoy = txRepository.obtenerTxDiff(canales, tiposArticulo, hoyInicio, hoyFin);
        return ComparativaTx.builder()
                .nombreHoja(nombreHoja)
                .titulo("Comparativa de ventas por minuto entre ayer y hoy para " + segmento)
                .ayer(ayer)
                .hoy(hoy)
                .build();
    }

    public Object obtenerDetalleTx(String atgOrderId, String atgShippingGroupId, String source) {
        log.info("Entrando a obtenerDetalleTx con los valores {} {}", atgOrderId, atgShippingGroupId);

        if ("QA2".equals(source)) {
            return crearRespuestaMockQA2();
        }

        List<CobroRow> cobroRowList = obtenerCobroBySource(atgOrderId, atgShippingGroupId, "LIV");
        if (cobroRowList == null || cobroRowList.isEmpty()) {
            log.warn("No se encontraron registros para el shipping group id: {}", atgShippingGroupId);
            throw new TxNotFound("No se encontraron registros para el shipping group id: " + atgShippingGroupId);
        }

        boolean empleado = false;
        boolean descuentoDe1erDiaAplicado = false;
        double descuentoAplicado = 0.0;
        for (CobroRow cobroRow : cobroRowList) {
            if (cobroRow.getImporteDesctoCasa() != null && cobroRow.getImporteDesctoCasa() > 0) {
                empleado = true;
            }

            if (cobroRow.getImporteDescto1erDia() != null && cobroRow.getImporteDescto1erDia() > 0) {
                descuentoDe1erDiaAplicado = true;
                descuentoAplicado = cobroRow.getImporteDescto1erDia();
            }
        }

        CobroResponse cobroResponse = new CobroResponse();
        cobroResponse.setMontoCobroMed(cobroRowList.getFirst().getCargoMed() != null ? cobroRowList.getFirst().getCargoMed() : 0.0);
        cobroResponse.setNumeroRemision(cobroRowList.getFirst().getRemision() != null ? Long.valueOf(cobroRowList.getFirst().getRemision()) : null);
        cobroResponse.setBcTransactionId(cobroRowList.getFirst().getId());
        cobroResponse.setNumeroSkus(cobroRowList.getFirst().getTotalSkus());
        cobroResponse.setMontoAbonoMed(cobroRowList.getFirst().getAbonoMed() != null ? cobroRowList.getFirst().getAbonoMed() : 0.0);
        cobroResponse.setTerminal(cobroRowList.getFirst().getTerminal());
        cobroResponse.setRecognitionStore(cobroRowList.getFirst().getRecognitionStore());
        cobroResponse.setCodigoRetorno(cobroRowList.getFirst().getIdCatEstatus() != null ? String.format("%02d", cobroRowList.getFirst().getIdCatEstatus()) : null);
        cobroResponse.setNoPedido(cobroRowList.getFirst().getPedido() != null ? String.format("%16s", cobroRowList.getFirst().getPedido()).replace(' ', '0') : null);
        cobroResponse.setEstadoTransaccion(cobroRowList.getFirst().getIdCatEstatus() == 0);
        cobroResponse.setMontoTotal(cobroRowList.getFirst().getTotalCobrado());
        cobroResponse.setCertificado(cobroRowList.getFirst().getCertificado() != null ? cobroRowList.getFirst().getCertificado() : cobroRowList.getFirst().getSeller());
        cobroResponse.setDescuentoAplicado(descuentoAplicado);
        cobroResponse.setDescuentoDe1erDiaAplicado(descuentoDe1erDiaAplicado);
        cobroResponse.setMensaje("La transacción ha sido completada exitosamente");
        cobroResponse.setNoAutorizacion(cobroRowList.getFirst().getAutorizacion());
        cobroResponse.setPaqueteriaOffLine(false);
        cobroResponse.setEmpleado(empleado);

        List<ItemsResponse> itemsResponses = new ArrayList<>();
        for (CobroRow cobroRow : cobroRowList) {
            log.info("CobroRow obtenido: {}", cobroRow);
            ItemsResponse itemsResponse = new ItemsResponse();
            itemsResponse.setCantidad(cobroRow.getCantidad());
            itemsResponse.setNoSeccion(cobroRow.getSeccion());
            itemsResponse.setDescuentoCasa(cobroRow.getImporteDesctoCasa());
            itemsResponse.setDescuentoFijo(cobroRow.getImporteDesctoFijo());
            itemsResponse.setIdSku(cobroRow.getSkuId());
            itemsResponse.setImporteTotal(cobroRow.getTotalSku());
            itemsResponse.setDescuentoPorcentual(cobroRow.getDescuentoPorcentual());
            itemsResponse.setFlete(cobroRow.getEsFlete() != null && cobroRow.getEsFlete() == 1);
            itemsResponse.setIsGift(cobroRow.getIsGift() != null && cobroRow.getIsGift().equals("Y"));
            itemsResponse.setSkuDescription(cobroRow.getDisplayName() != null ? cobroRow.getDisplayName() : "Descripción no disponible debido a db_link");
            itemsResponse.setPromoMed(cobroRow.getMonederoPromoValue() != null && cobroRow.getMonederoPromoValue() > 0);
            itemsResponse.setPromoMedType(cobroRow.getMonederoPromoType());
            itemsResponse.setPromoMedValue(cobroRow.getMonederoPromoValue());

            itemsResponses.add(itemsResponse);
        }

        cobroResponse.setItems(itemsResponses);
        log.info("CobroResponse construido: {}", cobroResponse);
        return cobroResponse;
    }

    private CobroResponse crearRespuestaMockQA2() {
        CobroResponse response = new CobroResponse();
        response.setBcTransactionId(1404678);
        response.setCertificado(111115);
        response.setNoPedido("0392603317783024");
        response.setNumeroRemision(4500000674L);
        response.setRecognitionStore("0001");
        response.setMontoTotal(279.3);
        response.setEstadoTransaccion(true);
        response.setCodigoRetorno("00");
        response.setTerminal(24);
        response.setNumeroSkus(1);
        response.setMontoAbonoMed(0.0);
        response.setMontoCobroMed(0.0);
        response.setDescuentoAplicado(0.0);
        response.setMensaje("La transacción ha sido completada exitosamente");
        response.setNoAutorizacion("370552");
        response.setPaqueteriaOffLine(false);
        response.setEmpleado(false);
        response.setDescuentoDe1erDiaAplicado(false);

        List<ItemsResponse> items = new ArrayList<>();
        ItemsResponse item = new ItemsResponse();
        item.setIdSku(1031970179L);
        item.setCantidad(1);
        item.setDescuentoCasa(0.0);
        item.setDescuentoFijo(0.0);
        item.setDescuentoPorcentual(30.0);
        item.setIdPromo(null);
        item.setImporteTotal(279.3);
        item.setIsGift(false);
        item.setNoSeccion(245);
        item.setPromoMed(false);
        item.setPromoMedType(0);
        item.setPromoMedValue(0.0);
        item.setSkuDescription("Playera Kenneth Cole");
        item.setTotalDescuento(null);
        item.setFlete(false);

        items.add(item);
        response.setItems(items);

        return response;
    }

    public List<CobroRow> obtenerCobroBySource(String atgOrderId, String atgShippingGroupId, String source) {
        log.info("Entrando a obtenerCobroBySource con los valores {} {} {}", atgOrderId, atgShippingGroupId, source);
        List<CobroRow> cobroRowList = null;
        if ("LIV".equals(source)) {
            cobroRowList = txRepository.obtenerCobroShippingGroup(atgOrderId, atgShippingGroupId);
        }

        return cobroRowList;
    }

    public List<TxPorMinuto> obtenerTransacciones() {
        var result = txRepository.obtenerSegmentoActual();
        var last = result.getFirst().getCurrentMin();
        var first = last.minusMinutes(5);

        return txRepository.obtenerTransacciones(first, last);
    }

    public List<TxPorMinuto> obtenerTransaccionesCache(LocalDateTime inicio, LocalDateTime fin) {
        return txRepository.obtenerTransaccionesCache(inicio, fin);
    }
}
