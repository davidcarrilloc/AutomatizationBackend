package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.CobroRow;
import com.mx.liverpool.automatizacionbackend.payload.response.CobroResponse;
import com.mx.liverpool.automatizacionbackend.repository.TxRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TxServiceTest {

    private CobroRow row(String sg, long sku) {
        CobroRow r = new CobroRow();
        r.setAtgShipGrpId(sg);
        r.setSkuId(sku);
        r.setIdCatEstatus(0);
        return r;
    }

    @Test
    void agrupaPorShippingGroupYMarcaNoEncontrados() {
        TxRepository repo = mock(TxRepository.class);
        List<String> sgs = List.of("sg1", "sg2", "sg3");
        when(repo.obtenerCobroShippingGroup("o1", sgs))
                .thenReturn(List.of(row("sg1", 1L), row("sg2", 2L), row("sg1", 3L)));

        List<CobroResponse> res = new TxService(repo).obtenerDetalleTx("o1", sgs, "LIVERPOOL");

        assertEquals(List.of("sg1", "sg2", "sg3"), res.stream().map(CobroResponse::getAtgShippingGroupId).toList());
        assertEquals(2, res.get(0).getItems().size());
        assertEquals(1, res.get(1).getItems().size());
        assertFalse(res.get(2).getEstadoTransaccion());
        assertNull(res.get(2).getItems());
    }

    @Test
    void regresaRemisionSiExisteSiNoOrdenVenta() {
        TxRepository repo = mock(TxRepository.class);
        List<String> sgs = List.of("sg1", "sg2");
        CobroRow conRemision = row("sg1", 1L);
        conRemision.setRemision("123");
        conRemision.setOrdenVenta("9849482237");
        CobroRow sinRemision = row("sg2", 2L);
        sinRemision.setOrdenVenta("9849482238");
        when(repo.obtenerCobroShippingGroup("o1", sgs)).thenReturn(List.of(conRemision, sinRemision));

        List<CobroResponse> res = new TxService(repo).obtenerDetalleTx("o1", sgs, "LIVERPOOL");

        assertEquals(123L, res.get(0).getNumeroRemision());
        assertNull(res.get(0).getOrdenVenta());
        assertNull(res.get(1).getNumeroRemision());
        assertEquals(9849482238L, res.get(1).getOrdenVenta());
    }

    @Test
    void qa2DevuelveMockPorCadaShippingGroup() {
        List<CobroResponse> res = new TxService(mock(TxRepository.class)).obtenerDetalleTx("o1", List.of("a", "b"), "QA2");

        assertEquals(List.of("a", "b"), res.stream().map(CobroResponse::getAtgShippingGroupId).toList());
        assertTrue(res.stream().allMatch(CobroResponse::getEstadoTransaccion));
    }
}
