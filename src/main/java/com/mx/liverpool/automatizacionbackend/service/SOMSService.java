package com.mx.liverpool.automatizacionbackend.service;

import com.mx.liverpool.automatizacionbackend.model.BC;
import com.mx.liverpool.automatizacionbackend.model.Hrd;
import com.mx.liverpool.automatizacionbackend.payload.response.SOMSResponse;
import com.mx.liverpool.automatizacionbackend.repository.AtgMirklRepository;
import com.mx.liverpool.automatizacionbackend.repository.TxRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@Log4j2
public class SOMSService {
    private final WebClient webClient;
    private final TxRepository txRepository;
    private final AtgMirklRepository atgMirklRepository;

    @Autowired
    public SOMSService(WebClient.Builder webClient, TxRepository txRepository, AtgMirklRepository atgMirklRepository) {
        this.webClient = webClient
                .baseUrl("http://172.17.212.7:6061")
                .build();
        this.txRepository = txRepository;
        this.atgMirklRepository = atgMirklRepository;
    }

    private Mono<List<String>> consultarRemisionesSinDatos() {
        return webClient.get()
                .uri("")
                .retrieve()
                .bodyToMono(String.class)
                .map(content -> Arrays.asList(content.split("\\r?\\n")))
                .doOnError(e -> log.error("Error al consultar remisiones sin datos: {}", e.getMessage()));
    }

    private List<String> limpiarCerosIzquierda(List<String> universo) {
        List<String> resultado = new ArrayList<>();
        for (String remision : universo) {
            resultado.add(remision.replaceFirst("^0+(?!$)", ""));
        }
        return resultado;
    }

    private Object ejecutarConsultas(List<String> universo) {
        universo = limpiarCerosIzquierda(universo);
        List<BC> resultSetCobro = txRepository.obtenerBC(universo);
        List<BC> atgCobradas = new ArrayList<>();
        List<BC> decommCobradas = new ArrayList<>();
        List<String> noCobradas = new ArrayList<>(universo);
        List<Hrd> hrd = new ArrayList<>();
        List<String> noHrd = new ArrayList<>();

        for (var element : resultSetCobro) {
            if (element.getAtgOrderId() == null) {
                atgCobradas.add(element);
            } else if (element.getAtgOrderId().startsWith("o")) {
                atgCobradas.add(element);
            } else {
                decommCobradas.add(element);
            }
        }

        noCobradas.removeAll(resultSetCobro.stream()
                .map(BC::getRemision)
                .toList());

        hrd = atgMirklRepository.obtenerHrd(atgCobradas.stream()
                .map(BC::getRemision)
                .toList());

        noHrd = new ArrayList<>(atgCobradas.stream()
                .map(BC::getRemision)
                .toList());

        noHrd.removeAll(hrd.stream()
                .map(Hrd::getTrackingNumber)
                .toList());

        return SOMSResponse.builder()
                .universo(universo)
                .atgCobradas(atgCobradas)
                .decommCobradas(decommCobradas)
                .noCobradas(noCobradas)
                .hrd(hrd)
                .noHrd(noHrd)
                .build();
    }

    public Object obtenerReporte() {
        var resultSet = consultarRemisionesSinDatos().block();
        return ejecutarConsultas(resultSet);
    }
}
