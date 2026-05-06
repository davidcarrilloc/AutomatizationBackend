package com.mx.liverpool.automatizacionbackend.websocket;

import com.mx.liverpool.automatizacionbackend.model.TxPorMinuto;
import com.mx.liverpool.automatizacionbackend.payload.request.DateRequest;
import com.mx.liverpool.automatizacionbackend.service.TxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Log4j2
@RequiredArgsConstructor
public class TxWebSocket implements WebSocketMessageBrokerConfigurer {
    private final TxService txService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-tx")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @MessageMapping("/enviarDetalle")
    @SendTo("/topic/notificaciones")
    public List<TxPorMinuto> recibirMensaje(DateRequest request) {
        log.info("Mensaje recibido vía socket: {} y {}", request.getFechaInicio().toString(), request.getFechaFin().toString());
        return txService.obtenerTransaccionesCache(request.getFechaInicio(), request.getFechaFin());
    }
}
