package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.exception.TxNotFound;
import com.mx.liverpool.automatizacionbackend.model.CobroRow;
import com.mx.liverpool.automatizacionbackend.payload.response.CobroResponse;
import com.mx.liverpool.automatizacionbackend.payload.response.ItemsResponse;
import com.mx.liverpool.automatizacionbackend.repository.TxQA2Repository;
import com.mx.liverpool.automatizacionbackend.repository.TxRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class TxService {
    private final TxRepository txRepository;
    private final TxQA2Repository txQA2Repository;

    @Autowired
    public TxService(TxRepository txRepository, TxQA2Repository txQA2Repository) {
        this.txRepository = txRepository;
        this.txQA2Repository = txQA2Repository;
    }

    public Object obtenerDiferenciaTxHoyvsAyer() {
        return null;
    }

    public Object obtenerDetalleTx(String atgOrderId, String atgShippingGroupId, String source) {
        log.info("Entrando a obtenerDetalleTx con los valores {} {}", atgOrderId, atgShippingGroupId);
        List<CobroRow> cobroRowList = obtenerCobroBySource(atgOrderId, atgShippingGroupId, source);
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
        cobroResponse.setOrdenVenta(cobroRowList.getFirst().getOrdenVenta() != null ? Long.valueOf(cobroRowList.getFirst().getOrdenVenta()) : null);
        cobroResponse.setBcTransactionId(cobroRowList.getFirst().getId());
        cobroResponse.setNumeroSkus(cobroRowList.getFirst().getTotalSkus());
        cobroResponse.setMontoAbonoMed(cobroRowList.getFirst().getAbonoMed() != null ? cobroRowList.getFirst().getAbonoMed() : 0.0);
        cobroResponse.setTerminal(cobroRowList.getFirst().getTerminal());
        cobroResponse.setRecognitionStore(cobroRowList.getFirst().getRecognitionStore());
        cobroResponse.setCodigoRetorno(cobroRowList.getFirst().getIdCatEstatus() != null ? String.format("%02d", cobroRowList.getFirst().getIdCatEstatus()) : null);
        cobroResponse.setNoPedido(cobroRowList.getFirst().getPedido() != null ? String.format("%16s", cobroRowList.getFirst().getPedido()).replace(' ', '0') : null);
        cobroResponse.setEstadoTransaccion(cobroRowList.getFirst().getIdCatEstatus() == 0);
        cobroResponse.setMontoTotal(cobroRowList.getFirst().getTotalCobrado());
        cobroResponse.setFechaTxCompra(cobroRowList.getFirst().getFechaTxCompra());
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
        return cobroResponse;
    }

    public List<CobroRow> obtenerCobroBySource(String atgOrderId, String atgShippingGroupId, String source) {
        log.info("Entrando a obtenerCobroBySource con los valores {} {} {}", atgOrderId, atgShippingGroupId, source);
        List<CobroRow> cobroRowList = null;
        if ("LIV".equals(source)) {
            cobroRowList = txRepository.obtenerCobroShippingGroup(atgOrderId, atgShippingGroupId);
        }

        if ("QA2".equals(source)) {
            cobroRowList = txQA2Repository.obtenerCobroShippingGroup(atgOrderId, atgShippingGroupId);
        }

        return cobroRowList;
    }
}
