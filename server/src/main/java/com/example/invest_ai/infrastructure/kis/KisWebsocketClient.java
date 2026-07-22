package com.example.invest_ai.infrastructure.kis;

import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.global.websocket.WebSocketSessionManager;
import com.example.invest_ai.infra.config.RedisKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * KIS WebSocket 실시간 시세 수집 클라이언트 (Production)
 *
 * - stocks 테이블에서 is_monitored=true 종목 조회 → 구독
 * - 실시간 체결가 수신 → Redis price:{stockCode}:current SET
 * - 연결 종료 감지 시 자동 재연결 + 구독 재등록
 */
@Slf4j
@Component
public class KisWebsocketClient {

    private final StringRedisTemplate redisTemplate;
    private final StockRepository stockRepository;
    private final WebSocketSessionManager sessionManager;
    private final WebSocketClient webSocketClient;
    private final ObjectMapper objectMapper;
    private final String websocketEndpoint;

    private Disposable connection;
    private volatile boolean running = true;

    public KisWebsocketClient(
            StringRedisTemplate redisTemplate,
            StockRepository stockRepository,
            WebSocketSessionManager sessionManager,
            @Value("${kis.api.websocket-endpoint}") String websocketEndpoint
    ) {
        this.redisTemplate = redisTemplate;
        this.stockRepository = stockRepository;
        this.sessionManager = sessionManager;
        this.websocketEndpoint = websocketEndpoint;
        this.webSocketClient = new ReactorNettyWebSocketClient();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void start() {
        log.info("🚀 KIS WebSocket 시세 수집기 시작");
        connect();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (connection != null && !connection.isDisposed()) {
            connection.dispose();
        }
        log.info("🛑 KIS WebSocket 시세 수집기 종료");
    }

    private void connect() {
        String approvalKey = redisTemplate.opsForValue().get(RedisKeys.kisApprovalKey());
        if (approvalKey == null || approvalKey.isEmpty()) {
            log.warn("⚠️ KIS approval_key가 Redis에 없습니다. 5초 후 재시도...");
            if (running) {
                Mono.delay(Duration.ofSeconds(5)).subscribe(t -> connect());
            }
            return;
        }

        List<Stock> stocks = stockRepository.findAllByIsMonitoredTrue();
        if (stocks.isEmpty()) {
            log.warn("⚠️ 구독할 종목이 없습니다 (is_monitored=true). 10초 후 재시도...");
            if (running) {
                Mono.delay(Duration.ofSeconds(10)).subscribe(t -> connect());
            }
            return;
        }

        String wsUri = websocketEndpoint + "?approval_key=" + approvalKey;
        log.info("→ KIS WebSocket 연결 시도: {} (구독 종목 {}개)", websocketEndpoint, stocks.size());

        connection = webSocketClient.execute(
                URI.create(wsUri),
                session -> {
                    log.info("✅ KIS WebSocket 연결 성공: session={}", session.getId());

                    // 모든 구독 종목에 대해 구독 메시지 전송
                    return session.send(
                            Mono.fromRunnable(() -> {
                                for (Stock stock : stocks) {
                                    String msg = createSubscribeMessage(approvalKey, stock.getStockCode());
                                    session.send(Mono.just(session.textMessage(msg)))
                                            .subscribe(
                                                    null,
                                                    err -> log.error("구독 전송 실패: {}", stock.getStockCode(), err),
                                                    () -> log.info("→ 구독 등록 완료: {}", stock.getStockCode())
                                            );
                                }
                            })
                    ).and(
                            session.receive()
                                    .map(WebSocketMessage::getPayloadAsText)
                                    .doOnNext(this::handleMessage)
                                    .then()
                    );
                }
        ).subscribe(
                null,
                err -> {
                    log.error("❌ KIS WebSocket 오류: {}", err.getMessage());
                    if (running) {
                        log.info("5초 후 재연결 시도...");
                        Mono.delay(Duration.ofSeconds(5)).subscribe(t -> connect());
                    }
                },
                () -> {
                    log.warn("⚠️ KIS WebSocket 정상 종료");
                    if (running) {
                        log.info("3초 후 재연결 시도...");
                        Mono.delay(Duration.ofSeconds(3)).subscribe(t -> connect());
                    }
                }
        );
    }

    private void handleMessage(String payload) {
        log.info("📩 [KIS RAW] {}", payload.substring(0, Math.min(300, payload.length())));
        try {
            // "\u0000" prefix 제거 (KIS WebSocket 특이사항)
            String cleaned = payload;
            if (cleaned.startsWith("0|") || cleaned.startsWith("\u0000")) {
                // pipe 구분 메시지를 먼저 시도
                if (cleaned.contains("|")) {
                    processPipeData(cleaned);
                    return;
                }
                // JSON 형태의 텍스트를 찾아서 파싱
                int braceIdx = cleaned.indexOf('{');
                if (braceIdx >= 0) {
                    cleaned = cleaned.substring(braceIdx);
                } else {
                    return; // 유효한 JSON 없음
                }
            }

            Map<String, Object> map = objectMapper.readValue(cleaned, new TypeReference<>() {});

            // 1. header.body check (KIS 응답 래퍼 구조)
            if (map.containsKey("body")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) map.get("body");
                if (body != null && body.containsKey("output")) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> output = (Map<String, String>) body.get("output");
                    if (output != null) {
                        storePrice(output);
                        return;
                    }
                }
                // body.rt_cd 체크
                String rtCd = String.valueOf(body.getOrDefault("rt_cd", ""));
                if ("0".equals(rtCd)) return;
            }

            // 2. 직접 output 필드가 있는 경우 (실시간 데이터 바로 옴)
            if (map.containsKey("MKSC_SHRN_ISCD")) {
                storePriceFromFlat(map);
                return;
            }

            // 3. header + body 구조
            if (map.containsKey("header")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> header = (Map<String, Object>) map.get("header");
                if (header != null && "0".equals(String.valueOf(header.get("tr_id")))) {
                    return; // 구독 확인 응답
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) map.get("body");
                if (body != null && body.containsKey("output")) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> output = (Map<String, String>) body.get("output");
                    if (output != null) {
                        storePrice(output);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("KIS 메시지 파싱 스킵: {}", e.getMessage());
        }
    }

    private void processPipeData(String payload) {
        try {
            // KIS pipe-delimited format: 0|H0STCNT0|001|005930^143058^259250^2^...
            String[] parts = payload.split("\\|");
            if (parts.length >= 4 && "H0STCNT0".equals(parts[1])) {
                // parts[3] = "005930^143058^259250^2^15250^..."  (^ 구분자)
                String[] fields = parts[3].split("\\^");
                if (fields.length >= 3) {
                    String stockCode = fields[0];   // e.g. 005930
                    String priceStr = fields[2];    // e.g. 259250 (현재 체결가)
                    redisTemplate.opsForValue().set(RedisKeys.priceCurrent(stockCode), priceStr);
                    log.debug("→ Redis SET price:{}:current = {}", stockCode, priceStr);

                    // 실시간 알림 브로드캐스트
                    long currentPrice = Long.parseLong(priceStr);
                    Map<String, Object> alert = Map.of(
                            "stockCode", stockCode,
                            "currentPrice", currentPrice
                    );
                    sessionManager.broadcast("PRICE_ALERT", alert);
                }
            }
        } catch (Exception e) {
            log.debug("Pipe 파싱 실패: {}", e.getMessage());
        }
    }

    /** Map<String, String>에서 stck_prpr(현재가) 추출 */
    private void storePrice(Map<String, String> output) {
        String stockCode = output.get("MKSC_SHRN_ISCD");
        String price = output.get("STCK_PRPR");
        if (stockCode != null && price != null && !price.isEmpty()) {
            redisTemplate.opsForValue().set(RedisKeys.priceCurrent(stockCode), price);
        }
    }

    /** Flat Map에서 종목코드 + 현재가 추출 (KIS 실시간 응답) */
    private void storePriceFromFlat(Map<String, Object> map) {
        String stockCode = String.valueOf(map.get("MKSC_SHRN_ISCD"));
        String price = String.valueOf(map.get("STCK_PRPR"));
        if (stockCode != null && price != null && !price.isEmpty() && !"null".equals(price)) {
            redisTemplate.opsForValue().set(RedisKeys.priceCurrent(stockCode), price);
        }
    }

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