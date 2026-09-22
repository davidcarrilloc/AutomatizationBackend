package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.CobroRow;
import com.mx.liverpool.automatizacionbackend.payload.response.CobroResponse;
import com.mx.liverpool.automatizacionbackend.payload.response.ItemsResponse;
import com.mx.liverpool.automatizacionbackend.repository.TxRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Log4j2
public class TxService {
    private final TxRepository txRepository;

    @Autowired
    public TxService(TxRepository txRepository) {
        this.txRepository = txRepository;
    }

    public List<CobroResponse> obtenerDetalleTx(String atgOrderId, List<String> atgShippingGroupIds, String source) {
        log.info("Entrando a obtenerDetalleTx con los valores {} {}", atgOrderId, atgShippingGroupIds);

        Map<String, List<CobroRow>> cobroRowsPorSg = "QA2".equals(source)
                ? Map.of()
                : obtenerCobroBySource(atgOrderId, atgShippingGroupIds, "LIVERPOOL").stream()
                        .collect(Collectors.groupingBy(CobroRow::getAtgShipGrpId));

        List<CobroResponse> responses = new ArrayList<>();
        for (String atgShippingGroupId : atgShippingGroupIds) {
            CobroResponse cobroResponse;
            if ("QA2".equals(source)) {
                cobroResponse = crearRespuestaMockQA2();
            } else if (cobroRowsPorSg.containsKey(atgShippingGroupId)) {
                cobroResponse = armarCobroResponse(cobroRowsPorSg.get(atgShippingGroupId));
            } else {
                log.warn("No se encontraron registros para el shipping group id: {}", atgShippingGroupId);
                cobroResponse = new CobroResponse();
                cobroResponse.setEstadoTransaccion(false);
                cobroResponse.setMensaje("No se encontraron registros para el shipping group id: " + atgShippingGroupId);
            }
            cobroResponse.setAtgShippingGroupId(atgShippingGroupId);
            responses.add(cobroResponse);
        }

        return responses;
    }

    private CobroResponse armarCobroResponse(List<CobroRow> cobroRowList) {
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

    public List<CobroRow> obtenerCobroBySource(String atgOrderId, List<String> atgShippingGroupIds, String source) {
        log.info("Entrando a obtenerCobroBySource con los valores {} {} {}", atgOrderId, atgShippingGroupIds, source);
        List<CobroRow> cobroRowList = List.of();
        if ("LIVERPOOL".equals(source)) {
            cobroRowList = txRepository.obtenerCobroShippingGroup(atgOrderId, atgShippingGroupIds);
        }

        return cobroRowList;
    }
}
