package com.example.invest_ai.kis.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisTestClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${kis.api.rest-base-url}")
    private String restBaseUrl;

    @Value("${kis.api.app-key}")
    private String appKey;

    @Value("${kis.api.app-secret}")
    private String appSecret;

    /**
     * Access Token 캐싱 (Lazy Loading)
     * - 최초 1회만 KIS API를 호출하여 발급
     * - 이후에는 캐싱된 값 재사용 (1분당 1회 제한 EGW00133 방지)
     */
    private String cachedAccessToken;

    /**
     * Approval Key 캐싱 (Lazy Loading)
     * - 최초 1회만 KIS API를 호출하여 발급
     * - WebSocket 연결 시 재사용
     */
    private String cachedApprovalKey;

    /**
     * 기능 ①: OAuth2 Access Token 발급 + WebSocket Approval Key 발급
     * .kisflow.txt §2 [플로우 1] 참조
     */
    public void issueTokens() {
        log.info("========== [KIS 테스트] 토큰 발급 시작 ==========");
        log.info("REST Base URL: {}", restBaseUrl);
        log.info("APP Key: {}", appKey);

        // 1. Access Token 발급 (캐싱된 토큰 재사용)
        String accessToken = getAccessToken();
        log.info("✅ [Access Token 발급 성공]");
        log.info("   access_token: {}", accessToken);

        // 2. Approval Key 발급 (POST /oauth2/Approval)
        Map<String, String> approvalResponse = fetchApprovalKey();
        String approvalKey = approvalResponse.get("approval_key");
        log.info("✅ [Approval Key 발급 성공]");
        log.info("   approval_key: {}", approvalKey);
        log.info("========== [KIS 테스트] 토큰 발급 완료 ==========");
    }

    /**
     * 기능 ②: REST 현재가 단발성 조회
     * GET /uapi/domestic-stock/v1/quotations/inquire-price
     * .kisflow.txt §4 REST API 참조
     */
    public void fetchCurrentPrice(String stockCode) {
        log.info("========== [KIS 테스트] 현재가 조회 시작 ==========");
        log.info("종목코드: {}", stockCode);

        // 캐싱된 Access Token 재사용 (새 발급 요청 안 함)
        String accessToken = getAccessToken();

        if (accessToken == null) {
            log.error("❌ [현재가 조회 실패] access_token이 null - 토큰 발급 단계를 확인하세요");
            return;
        }

        WebClient webClient = webClientBuilder.baseUrl(restBaseUrl).build();

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .build())
                    .header("authorization", "Bearer " + accessToken)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", "FHKST01010100")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("✅ [현재가 조회 성공] 종목: {}", stockCode);
            log.info("--- Raw JSON 응답 ---");
            log.info(response);
            log.info("----------------------");
        } catch (Exception e) {
            log.error("❌ [현재가 조회 실패] {}", e.getMessage());
        }
        log.info("========== [KIS 테스트] 현재가 조회 완료 ==========");
    }

    /**
     * Access Token 조회 (Lazy Loading)
     * - 캐싱된 토큰이 있으면 즉시 반환
     * - null일 때만 KIS API 호출 후 캐싱
     */
    private String getAccessToken() {
        if (cachedAccessToken != null) {
            log.info("   (캐싱된 access_token 재사용)");
            return cachedAccessToken;
        }
        cachedAccessToken = requestNewAccessToken();
        return cachedAccessToken;
    }

    /**
     * Access Token 실제 발급 (POST /oauth2/token)
     * - KIS API 1분당 1회 제한(EGW00133) 준수를 위해 getAccessToken() 통해서만 호출
     */
    private String requestNewAccessToken() {
        WebClient webClient = webClientBuilder.baseUrl(restBaseUrl).build();

        try {
            String body = webClient.post()
                    .uri("/oauth2/tokenP")
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", appKey,
                            "appsecret", appSecret
                    ))
                    .exchangeToMono(response ->
                            response.bodyToMono(String.class)
                                    .map(res -> {
                                        log.info("STATUS = {}", response.statusCode());
                                        log.info("BODY = {}", res);
                                        return res;
                                    }))
                    .block();

            if (body == null || body.isBlank()) {
                log.error("❌ [Access Token 발급 실패] 응답 body가 비어 있음");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = new ObjectMapper().readValue(body, Map.class);

            String accessToken = (String) response.get("access_token");
            String tokenType = (String) response.get("token_type");
            Integer expiresIn = (Integer) response.get("expires_in");

            if (accessToken == null) {
                log.error("❌ [Access Token 발급 실패] access_token이 null - BODY: {}", body);
                return null;
            }

            log.info("   ← access_token: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));
            log.info("   ← token_type: {}", tokenType);
            log.info("   ← expires_in: {}초", expiresIn);

            return accessToken;
        } catch (Exception e) {
            log.error("❌ [Access Token 발급 실패] {}", e.getMessage());
            return null;
        }
    }

    /**
     * Approval Key 조회 (Lazy Loading)
     * - 캐싱된 키가 있으면 즉시 반환
     * - null일 때만 KIS API 호출 후 캐싱
     */
    public String getApprovalKey() {
        if (cachedApprovalKey != null) {
            log.info("   (캐싱된 approval_key 재사용)");
            return cachedApprovalKey;
        }
        Map<String, String> response = fetchApprovalKey();
        cachedApprovalKey = response.get("approval_key");
        return cachedApprovalKey;
    }

    /**
     * Approval Key 발급 (POST /oauth2/Approval)
     */
    private Map<String, String> fetchApprovalKey() {
        WebClient webClient = webClientBuilder.baseUrl(restBaseUrl).build();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/oauth2/Approval")
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", appKey,
                            "secretkey", appSecret
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String approvalKey = (String) response.get("approval_key");
            log.info("   ← approval_key: {}", approvalKey);

            return Map.of("approval_key", approvalKey);
        } catch (Exception e) {
            log.error("❌ [Approval Key 발급 실패] {}", e.getMessage());
            throw new RuntimeException("KIS Approval Key 발급 실패", e);
        }
    }
}