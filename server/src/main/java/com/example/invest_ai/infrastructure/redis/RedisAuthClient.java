package com.example.invest_ai.infrastructure.redis;

import com.example.invest_ai.infra.config.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 인증 관련 클라이언트
 *
 * - Refresh Token: auth:{userId}:refreshToken (TTL 14일)
 * - Access Token 블랙리스트: auth:{jti}:blacklist
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAuthClient {

    private final StringRedisTemplate redisTemplate;

    /** Refresh Token 저장 (14일) */
    public void saveRefreshToken(Long userId, String refreshToken) {
        String key = RedisKeys.authRefreshToken(userId);
        redisTemplate.opsForValue().set(key, refreshToken, Duration.ofDays(14));
        log.info("Redis Refresh Token 저장: {}", key);
    }

    /** Refresh Token 조회 */
    public String getRefreshToken(Long userId) {
        return redisTemplate.opsForValue().get(RedisKeys.authRefreshToken(userId));
    }

    /** Refresh Token 삭제 (로그아웃 시) */
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(RedisKeys.authRefreshToken(userId));
        log.info("Redis Refresh Token 삭제: {}", userId);
    }

    /** Access Token 블랙리스트 등록 (TTL = 토큰 잔여시간) */
    public void addBlacklist(String jti, long remainingSeconds) {
        String key = RedisKeys.authBlacklist(jti);
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(remainingSeconds));
        log.info("Redis 블랙리스트 등록: {} (TTL: {}초)", key, remainingSeconds);
    }

    /** 블랙리스트 확인 */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.authBlacklist(jti)));
    }
}