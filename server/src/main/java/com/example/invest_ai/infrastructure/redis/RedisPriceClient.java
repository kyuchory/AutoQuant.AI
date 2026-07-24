package com.example.invest_ai.infrastructure.redis;

import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infra.config.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 시세 조회 클라이언트
 *
 * price:{stockCode}:current 키에서 현재 체결가를 조회합니다.
 * Writer: KisWebsocketClient (1단계 구현 완료)
 * Reader: AssetSummaryService, 조건 매칭 워커
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPriceClient {

    private final StringRedisTemplate redisTemplate;

    /** 단일 종목 현재가 조회 */
    public BigDecimal getCurrentPrice(String stockCode) {
        String key = RedisKeys.priceCurrent(stockCode);
        String value = redisTemplate.opsForValue().get(key);

        if (value == null || value.isEmpty()) {
            throw new CustomException(ErrorCode.PRICE_UNAVAILABLE, "현재 시세를 조회할 수 없습니다. (종목: " + stockCode + ")");
        }

        return new BigDecimal(value);
    }

    /** 전일대비 등락률 조회 (Redis miss 시 null 반환) */
    public Double getChangeRate(String stockCode) {
        String key = RedisKeys.priceChangeRate(stockCode);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isEmpty()) return null;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return null; }
    }

    /** 전일대비 등락률 저장 (TTL 24시간) */
    public void setChangeRate(String stockCode, double changeRate) {
        String key = RedisKeys.priceChangeRate(stockCode);
        redisTemplate.opsForValue().set(key, String.valueOf(changeRate), Duration.ofHours(24));
    }

    /** 여러 종목 현재가 일괄 조회 */
    public Map<String, BigDecimal> getAllCurrentPrices(List<String> stockCodes) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (String stockCode : stockCodes) {
            try {
                result.put(stockCode, getCurrentPrice(stockCode));
            } catch (CustomException e) {
                // 조회 실패 시 0으로 처리
                log.warn("시세 조회 실패 (0 처리): stockCode={}", stockCode);
                result.put(stockCode, BigDecimal.ZERO);
            }
        }
        return result;
    }
}