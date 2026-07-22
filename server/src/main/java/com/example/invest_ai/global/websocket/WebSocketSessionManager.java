package com.example.invest_ai.global.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자 WebSocket 세션 관리자
 *
 * userId → WebSocketSession 매핑을 ConcurrentHashMap으로 관리.
 * 알림 전송 시 Service 계층에서 sendMessage()를 호출.
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 세션 등록 */
    public void addSession(Long userId, WebSocketSession session) {
        sessions.put(userId, session);
        log.info("WebSocket 세션 등록: userId={}", userId);
    }

    /** 세션 제거 */
    public void removeSession(Long userId) {
        sessions.remove(userId);
        log.info("WebSocket 세션 제거: userId={}", userId);
    }

    /**
     * 특정 유저에게 메시지 전송
     * 세션이 없거나 닫혀있으면 조용히 skip
     */
    public void sendMessage(Long userId, String type, Object payload) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            if (session == null) {
                // 세션 없음 — skip
            } else {
                removeSession(userId);
            }
            return;
        }

        try {
            Map<String, Object> message = Map.of("type", type, "payload", payload);
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("WebSocket 메시지 전송 실패: userId={}", userId, e);
        }
    }

    /** 접속 중인 모든 유저에게 메시지 브로드캐스트 */
    public void broadcast(String type, Object payload) {
        try {
            Map<String, Object> message = Map.of("type", type, "payload", payload);
            String json = objectMapper.writeValueAsString(message);
            for (Map.Entry<Long, WebSocketSession> entry : sessions.entrySet()) {
                WebSocketSession session = entry.getValue();
                if (session != null && session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(json));
                        }
                    } catch (IOException e) {
                        log.warn("브로드캐스트 실패: userId={}", entry.getKey(), e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("브로드캐스트 직렬화 실패", e);
        }
    }
}
