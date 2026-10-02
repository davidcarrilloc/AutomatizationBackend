package com.mx.liverpool.automatizacionbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class MemoriaService {

    private final FulfillmentService fulfillmentService;
    private final ReprocesoCompletoAsyncService reprocesoCompletoAsyncService;
    private final AvailabilityService availabilityService;
    private final FachadaService fachadaService;
    private final StatusOmsService statusOmsService;
    private final ReenvioService reenvioService;

    public Map<String, Object> liberar() {
        log.info("Entrando a liberar");
        Map<String, Object> liberados = new LinkedHashMap<>();
        liberados.put("fulfillment", fulfillmentService.liberarMemoria());
        liberados.put("reprocesoCompleto", reprocesoCompletoAsyncService.liberarMemoria());
        liberados.put("availability", availabilityService.liberarMemoria());
        liberados.put("fachada", fachadaService.liberarMemoria());
        liberados.put("fulfillmentTxt", statusOmsService.liberarMemoria());
        liberados.put("reenvio", reenvioService.liberarMemoria());
        log.info("Finalizando liberar: {}", liberados);
        return liberados;
    }
}
