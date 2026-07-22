package com.example.invest_ai.infrastructure.kis;

import com.example.invest_ai.infra.config.RedisKeys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * KIS OAuth 인증 클라이언트 (Production)
 *
 * - 서버 시작 시 KIS access_token + approval_key 발급 → Redis 저장
 * - 5.5시간 주기로 선제 갱신 (KIS 공식 24시간, 권장 6시간 갱신)
 * - Redis TTL: 5시간 50분 (21000초)
 */
@Slf4j
@Component
public class KisAuthClient {

    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient;
    private final String appKey;
    private final String appSecret;

    public KisAuthClient(
            StringRedisTemplate redisTemplate,
            @Value("${kis.api.rest-base-url}") String restBaseUrl,
            @Value("${kis.api.app-key}") String appKey,
            @Value("${kis.api.app-secret}") String appSecret
    ) {
        this.redisTemplate = redisTemplate;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.webClient = WebClient.builder().baseUrl(restBaseUrl).build();
    }

    @PostConstruct
    public void init() {
        log.info("🔑 KIS 인증 토큰 초기 발급 시작");
        issueTokens();
        log.info("🔑 KIS 인증 토큰 초기 발급 완료");
    }

    /** 5.5시간(19800초) 주기로 토큰 갱신 */
    @Scheduled(fixedRate = 19800000)
    public void scheduledRefresh() {
        log.info("⏰ KIS 토큰 정기 갱신 시작");
        issueTokens();
    }

    private void issueTokens() {
        try {
            // 1. Access Token 발급
            String accessToken = fetchAccessToken();
            if (accessToken != null) {
                redisTemplate.opsForValue().set(
                        RedisKeys.kisAccessToken(), accessToken,
                        Duration.ofSeconds(21000)); // 5시간 50분
                log.info("✅ KIS Access Token 발급 및 Redis 저장 완료");
            }

            // 2. Approval Key 발급
            String approvalKey = fetchApprovalKey();
            if (approvalKey != null) {
                redisTemplate.opsForValue().set(
                        RedisKeys.kisApprovalKey(), approvalKey,
                        Duration.ofSeconds(21000)); // 5시간 50분
                log.info("✅ KIS Approval Key 발급 및 Redis 저장 완료");
            }
        } catch (Exception e) {
            log.error("❌ KIS 토큰 발급 실패: {}", e.getMessage());
        }
    }

    private String fetchAccessToken() {
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

            if (response == null) return null;
            String token = (String) response.get("access_token");
            if (token != null) {
                log.info("← KIS access_token: {}...", token.substring(0, Math.min(20, token.length())));
            }
            return token;
        } catch (Exception e) {
            log.error("KIS Access Token 발급 실패", e);
            return null;
        }
    }

    private String fetchApprovalKey() {
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

            if (response == null) return null;
            String key = (String) response.get("approval_key");
            log.info("← KIS approval_key: {}", key);
            return key;
        } catch (Exception e) {
            log.error("KIS Approval Key 발급 실패", e);
            return null;
        }
    }
}