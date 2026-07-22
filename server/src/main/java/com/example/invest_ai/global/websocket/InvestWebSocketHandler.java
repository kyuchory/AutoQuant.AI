package com.example.invest_ai.global.websocket;

import com.example.invest_ai.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

/**
 * 사용자용 WebSocket 핸들러
 *
 * 연결 시 JWT 검증 → userId 파싱 → SessionManager 등록
 * 연결 종료 시 SessionManager에서 제거
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvestWebSocketHandler extends TextWebSocketHandler {

    private final JwtProvider jwtTokenProvider;
    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null || !jwtTokenProvider.validate(token)) {
            log.warn("WebSocket 연결 거부: 유효하지 않은 토큰");
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception ignored) {
            }
            return;
        }

        Long userId = jwtTokenProvider.getUserId(token);
        sessionManager.addSession(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 클라이언트 → 서버 메시지는 현재 사용하지 않음 (브라우저는 구독만 함)
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String token = extractToken(session);
        if (token != null && jwtTokenProvider.validate(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            sessionManager.removeSession(userId);
        }
    }

    /** 쿼리 파라미터에서 token 추출 */
    private String extractToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && "token".equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}