package com.example.invest_ai.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 사용자용 WebSocket 설정
 *
 * endpoint: /ws
 * 쿼리 파라미터로 token 전달 → ws?token={accessToken}
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final InvestWebSocketHandler investWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(investWebSocketHandler, "/ws")
                .setAllowedOrigins("http://localhost:3000");
    }
}