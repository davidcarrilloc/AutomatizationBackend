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

@Service
@Log4j2
public class TxService {
    private final TxRepository txRepository;

    @Autowired
    public TxService(TxRepository txRepository) {
        this.txRepository = txRepository;
    }

    public Object obtenerDiferenciaTxHoyvsAyer() {
        return null;
    }

    public Object obtenerDetalleTx(String atgOrderId, String atgShippingGroupId) {
        log.info("Entrando a obtenerDetalleTx con los valores {} {}", atgOrderId, atgShippingGroupId);
        List<CobroRow> cobroRowList = txRepository.obtenerCobroShippingGroup(atgOrderId, atgShippingGroupId);
        if (cobroRowList == null || cobroRowList.isEmpty()) {
            log.warn("No se encontraron registros para el shipping group id: {}", atgShippingGroupId);
            throw new RuntimeException("No se encontraron registros para el shipping group id: " + atgShippingGroupId);
        }

        CobroResponse cobroResponse = new CobroResponse();
        cobroResponse.setMontoCobroMed(cobroRowList.getFirst().getCargoMed());
        cobroResponse.setNumeroRemision(cobroRowList.getFirst().getRemision());
        cobroResponse.setOrdenVenta(cobroRowList.getFirst().getOrdenVenta());
        cobroResponse.setBcTransactionId(cobroRowList.getFirst().getId());
        cobroResponse.setNumeroSkus(cobroRowList.getFirst().getTotalSkus());
        cobroResponse.setMontoAbonoMed(cobroRowList.getFirst().getAbonoMed());
        cobroResponse.setTerminal(cobroRowList.getFirst().getTerminal());
        cobroResponse.setRecognitionStore(cobroRowList.getFirst().getRecognitionStore());
        cobroResponse.setCodigoRetorno(cobroRowList.getFirst().getIdCatEstatus());
        cobroResponse.setNoPedido(cobroRowList.getFirst().getPedido());
        cobroResponse.setEstadoTransaccion(cobroRowList.getFirst().getIdCatEstatus() == 0);
        cobroResponse.setMontoTotal(cobroRowList.getFirst().getTotalCobrado());
        cobroResponse.setFechaTxCompra(cobroRowList.getFirst().getFechaTxCompra());

        List<ItemsResponse> itemsResponses = new ArrayList<>();
        for (CobroRow cobroRow : cobroRowList) {
            log.info("CobroRow obtenido: {}", cobroRow);
            ItemsResponse itemsResponse = new ItemsResponse();
            itemsResponse.setCantidad(cobroRow.getCantidad());
            itemsResponse.setNoSeccion(cobroRow.getSeccion());
            itemsResponse.setDescuentoDe1erDiaAplicado(cobroRow.getImporteDescto1erDia() != null && cobroRow.getImporteDescto1erDia() > 0);
            itemsResponse.setDescuentoDe1erDia(cobroRow.getImporteDescto1erDia());
            itemsResponse.setCouponId(null);
            itemsResponse.setDescuentoCasa(cobroRow.getImporteDesctoCasa());
            itemsResponse.setDescuentoFijo(cobroRow.getImporteDesctoFijo());
            itemsResponse.setIdSku(cobroRow.getSkuId());
            itemsResponse.setImporteTotal(cobroRow.getTotalSku());
            itemsResponse.setDescuentoPorcentual(cobroRow.getDescuentoPorcentual());
            itemsResponse.setEsFlete(cobroRow.getEsFlete());
            itemsResponse.setIsGift(cobroRow.getIsGift() != null && cobroRow.getIsGift().equals("Y"));
            itemsResponse.setEmpleado(cobroRow.getImporteDesctoCasa() != null && cobroRow.getImporteDesctoCasa() > 0);
            itemsResponse.setSkuDescription(cobroRow.getDisplayName());

            itemsResponses.add(itemsResponse);
        }

        cobroResponse.setItems(itemsResponses);
        return cobroResponse;
    }
}
