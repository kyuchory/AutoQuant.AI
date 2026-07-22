package com.example.invest_ai.infrastructure.kis;

import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infra.config.RedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * KIS REST API — 모의투자 주문 클라이언트
 *
 * BUY: VTTC0802U, SELL: VTTC0801U
 * CANO = account-no (8자리), ACNT_PRDT_CD = account-prdt-cd ("01")
 */
@Slf4j
@Component
public class KisOrderClient {

    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient;
    private final String appKey;
    private final String appSecret;
    private final String accountNo;
    private final String accountPrdtCd;

    public KisOrderClient(
            StringRedisTemplate redisTemplate,
            @Value("${kis.api.rest-base-url}") String baseUrl,
            @Value("${kis.api.app-key}") String appKey,
            @Value("${kis.api.app-secret}") String appSecret,
            @Value("${kis.api.account-no}") String accountNo,
            @Value("${kis.api.account-prdt-cd}") String accountPrdtCd
    ) {
        this.redisTemplate = redisTemplate;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.accountNo = accountNo;
        this.accountPrdtCd = accountPrdtCd;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * KIS 모의투자 주문 실행
     *
     * @param stockCode 종목코드 (예: 005930)
     * @param orderType BUY 또는 SELL
     * @param quantity  주문 수량
     */
    public void executeOrder(String stockCode, String orderType, int quantity) {
        // Redis에서 KIS Access Token 조회
        String accessToken = redisTemplate.opsForValue().get(RedisKeys.kisAccessToken());
        if (accessToken == null || accessToken.isEmpty()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "KIS Access Token이 없습니다.");
        }

        String trId = "BUY".equalsIgnoreCase(orderType) ? "VTTC0802U" : "VTTC0801U";

        // KIS 주문 바디 — 모든 숫자 필드는 String으로 전송
        Map<String, String> body = Map.of(
                "CANO", accountNo,
                "ACNT_PRDT_CD", accountPrdtCd,
                "PDNO", stockCode,
                "ORD_DVSN", "01",          // 시장가
                "ORD_QTY", String.valueOf(quantity),
                "ORD_UNPR", "0"            // 시장가 주문 시 0
        );

        log.info("→ KIS 주문 요청: trId={}, stockCode={}, orderType={}, qty={}", trId, stockCode, orderType, quantity);

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/uapi/domestic-stock/v1/trading/order-cash")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", trId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("← KIS 주문 응답: {}", response);

            if (response != null) {
                String rtCd = String.valueOf(response.get("rt_cd"));
                if (!"0".equals(rtCd)) {
                    String msg = response.get("msg1") != null ? String.valueOf(response.get("msg1")) : "알 수 없는 오류";
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "KIS 주문 실패: " + msg);
                }
                log.info("← KIS 주문 체결 성공: stockCode={}, qty={}", stockCode, quantity);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("KIS API 통신 오류", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "KIS 주문 API 통신 실패: " + e.getMessage());
        }
    }
}