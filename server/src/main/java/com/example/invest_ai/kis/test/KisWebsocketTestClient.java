package com.example.invest_ai.kis.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisWebsocketTestClient {

    private final KisTestClient kisTestClient;

    @Value("${kis.api.websocket-endpoint}")
    private String websocketEndpoint;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * KIS WebSocket에 연결하여 실시간 체결가를 구독합니다.
     * .kisflow.txt §3 [플로우 2] 참조
     *
     * @param stockCode 구독할 종목 코드 (예: "005930")
     */
    public void connectAndSubscribe(String stockCode) {
        log.info("========== [KIS WebSocket 테스트] 연결 시작 ==========");
        log.info("WebSocket Endpoint: {}", websocketEndpoint);
        log.info("구독 종목: {}", stockCode);

        // 1. Approval Key 조회 (KisTestClient에서 캐싱된 값 사용)
        String approvalKey = kisTestClient.getApprovalKey();
        if (approvalKey == null) {
            log.error("❌ [WebSocket 연결 실패] approval_key가 null입니다.");
            return;
        }
        log.info("approval_key: {}", approvalKey);

        // 2. WebSocket URI 생성 (approval_key를 쿼리 파라미터로 전달)
        String wsUri = websocketEndpoint + "?approval_key=" + approvalKey;
        log.info("WebSocket URI: {}", wsUri);

        // 3. WebSocket 클라이언트 생성 및 연결
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

        try {
            client.execute(
                    URI.create(wsUri),
                    session -> {
                        log.info("✅ [WebSocket 연결 성공] 세션 ID: {}", session.getId());

                        // 3-1. 구독 메시지 전송 (H0STCNT0: 실시간 체결가) — header + body 구조 사용
                        String subscribeMessage = createSubscribeMessage(approvalKey, stockCode);
                        log.info("→ 구독 메시지 전송: {}", subscribeMessage);

                        return session.send(
                                        Mono.just(session.textMessage(subscribeMessage))
                                )
                                .thenMany(
                                        // 3-2. 서버로부터 오는 메시지를 계속 수신
                                        session.receive()
                                                .map(WebSocketMessage::getPayloadAsText)
                                                .doOnNext(payload -> {
                                                    log.info("📩 [WebSocket 수신]");
                                                    log.info("{}", payload);
                                                })
                                )
                                .then();
                    }
            ).block(); // 테스트이므로 block()으로 대기

        } catch (Exception e) {
            log.error("❌ [WebSocket 오류] {}", e.getMessage());
        }

        log.info("========== [KIS WebSocket 테스트] 연결 종료 ==========");
    }

    /**
     * KIS WebSocket 구독 메시지를 생성합니다.
     * Tr_ID: H0STCNT0 (실시간 체결가)
     *
     * KIS WebSocket 메시지 규격:
     * - header: approval_key, personalseckey, custtype, tr_id, tr_key
     * - body.input: tr_id, tr_key
     */
    private String createSubscribeMessage(String approvalKey, String stockCode) {
        try {
            Map<String, Object> message = Map.of(
                    "header", Map.of(
                            "approval_key", approvalKey,
                            "personalseckey", "1",
                            "custtype", "P",
                            "tr_id", "H0STCNT0",
                            "tr_key", stockCode,
                            "tr_type", "1"
                    ),
                    "body", Map.of(
                            "input", Map.of(
                                    "tr_id", "H0STCNT0",
                                    "tr_key", stockCode
                            )
                    )
            );
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("구독 메시지 생성 실패", e);
            return "{\"header\":{\"approval_key\":\"" + approvalKey
                    + "\",\"personalseckey\":\"1\",\"custtype\":\"P\",\"tr_id\":\"H0STCNT0\",\"tr_key\":\""
                    + stockCode + "\",\"tr_type\":\"1\"},\"body\":{\"input\":{\"tr_id\":\"H0STCNT0\",\"tr_key\":\""
                    + stockCode + "\"}}}";
        }
    }
}