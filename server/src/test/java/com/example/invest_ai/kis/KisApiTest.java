package com.example.invest_ai.kis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class KisApiTest {

    private final WebClient.Builder webClientBuilder = WebClient.builder();

    @Value("${kis.api.rest-base-url}")
    private String restBaseUrl;

    @Value("${kis.api.app-key}")
    private String appKey;

    @Value("${kis.api.app-secret}")
    private String appSecret;

    @Value("${kis.api.account-number}")
    private String accountNumber;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("KIS Access Token 발급 및 Approval Key 발급 성공 검증")
    void testIssueTokens() {
        log.info("======================================================");
        log.info("  KIS 토큰 발급 테스트 시작");
        log.info("======================================================");

        // Access Token 발급
        String accessToken = requestAccessToken();
        assertNotNull(accessToken, "Access Token은 null이 아니어야 합니다");
        assertFalse(accessToken.isBlank(), "Access Token은 비어있지 않아야 합니다");
        log.info("✅ Access Token 발급 성공: {}...", accessToken.substring(0, 20));

        // Approval Key 발급
        String approvalKey = requestApprovalKey();
        assertNotNull(approvalKey, "Approval Key는 null이 아니어야 합니다");
        assertFalse(approvalKey.isBlank(), "Approval Key는 비어있지 않아야 합니다");
        log.info("✅ Approval Key 발급 성공: {}", approvalKey);

        log.info("======================================================");
        log.info("  KIS 토큰 발급 테스트 완료");
        log.info("======================================================");
    }

    @Test
    @DisplayName("KIS REST 현재가 조회 성공 검증 (삼성전자 005930)")
    void testFetchCurrentPrice() {
        log.info("======================================================");
        log.info("  KIS 현재가 조회 테스트 시작 (종목: 005930)");
        log.info("======================================================");

        // 1. Access Token 발급
        String accessToken = requestAccessToken();
        assertNotNull(accessToken);

        // 2. 현재가 조회
        WebClient webClient = webClientBuilder.baseUrl(restBaseUrl).build();
        String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", "005930")
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010100")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertNotNull(response, "현재가 응답은 null이 아니어야 합니다");
        log.info("📊 [삼성전자 현재가 응답 수신]");

        // 핵심 필드 검증
        assertTrue(response.contains("stck_prpr"), "응답에 stck_prpr(현재가) 필드가 포함되어야 합니다");
        assertTrue(response.contains("rt_cd"), "응답에 rt_cd(결과코드) 필드가 포함되어야 합니다");
        assertTrue(response.contains("msg1"), "응답에 msg1(메시지) 필드가 포함되어야 합니다");

        // 현재가 값 출력
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) parsed.get("output");
            String currentPrice = (String) output.get("stck_prpr");
            String changeRate = (String) output.get("prdy_ctrt");
            log.info("   현재가: {}원 (등락률: {}%)", currentPrice, changeRate);
        } catch (Exception e) {
            log.warn("JSON 파싱 불가, raw 응답 출력");
        }

        log.info("✅ 현재가 조회 성공");
        log.info("======================================================");
        log.info("  KIS 현재가 조회 테스트 완료");
        log.info("======================================================");
    }

    /** Access Token 발급 (POST /oauth2/tokenP) */
    private String requestAccessToken() {
        WebClient webClient = webClientBuilder.baseUrl(restBaseUrl).build();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/oauth2/tokenP")
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", appKey,
                            "appsecret", appSecret
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String accessToken = (String) response.get("access_token");
            log.info("   access_token: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));
            log.info("   token_type: {}", response.get("token_type"));
            log.info("   expires_in: {}초", response.get("expires_in"));
            return accessToken;
        } catch (Exception e) {
            log.error("❌ Access Token 발급 실패: {}", e.getMessage());
            return null;
        }
    }

    /** Approval Key 발급 (POST /oauth2/Approval) */
    private String requestApprovalKey() {
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
            log.info("   approval_key: {}", approvalKey);
            return approvalKey;
        } catch (Exception e) {
            log.error("❌ Approval Key 발급 실패: {}", e.getMessage());
            return null;
        }
    }
}