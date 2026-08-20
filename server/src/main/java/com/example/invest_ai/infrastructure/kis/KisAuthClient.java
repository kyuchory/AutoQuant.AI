package com.example.invest_ai.infrastructure.kis;

import com.example.invest_ai.infra.config.RedisKeys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRY = 5;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient;
    private final String appKey;
    private final String appSecret;
    private final AtomicInteger retryCount = new AtomicInteger(0);

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

    /** 재발급 없이 재사용할 수 있다고 볼 최소 남은 TTL (개발 중 잦은 재시작이 KIS rate limit에 걸리는 것을 방지) */
    private static final long MIN_REUSABLE_TTL_SECONDS = 60;

    @PostConstruct
    public void init() {
        if (hasValidCachedTokens()) {
            log.info("🔑 Redis에 유효한 KIS 토큰이 이미 있어 재사용합니다 (재발급 스킵)");
            return;
        }
        log.info("🔑 KIS 인증 토큰 초기 발급 시작");
        issueTokens();
        log.info("🔑 KIS 인증 토큰 초기 발급 완료");
    }

    /** 서버 재시작 시 Redis에 아직 유효한 토큰/approval_key가 남아있는지 확인 */
    private boolean hasValidCachedTokens() {
        Long accessTtl = redisTemplate.getExpire(RedisKeys.kisAccessToken());
        Long approvalTtl = redisTemplate.getExpire(RedisKeys.kisApprovalKey());
        return accessTtl != null && accessTtl > MIN_REUSABLE_TTL_SECONDS
                && approvalTtl != null && approvalTtl > MIN_REUSABLE_TTL_SECONDS;
    }

    /** 5.5시간(19800초) 주기로 토큰 갱신 (최초 120초 지연 → 초기 발급과 충돌 방지) */
    @Scheduled(fixedRate = 19800000, initialDelay = 120000)
    public void scheduledRefresh() {
        log.info("⏰ KIS 토큰 정기 갱신 시작");
        issueTokens();
    }

    private void issueTokens() {
        boolean success;
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
            success = accessToken != null && approvalKey != null;
        } catch (Exception e) {
            log.error("❌ KIS 토큰 발급 실패: {}", e.getMessage());
            success = false;
        }

        if (success) {
            retryCount.set(0);
            return;
        }

        int attempt = retryCount.incrementAndGet();
        if (attempt > MAX_RETRY) {
            log.error("❌ KIS 토큰 발급 {}회 연속 실패 — 다음 정기 갱신 주기까지 재시도를 중단합니다.", attempt - 1);
            return;
        }
        log.warn("⏳ KIS 토큰 발급 실패 — {}초 후 재시도 ({}/{})", RETRY_DELAY.getSeconds(), attempt, MAX_RETRY);
        Mono.delay(RETRY_DELAY).subscribe(t -> issueTokens());
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
                    .timeout(CALL_TIMEOUT)
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
                    .timeout(CALL_TIMEOUT)
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