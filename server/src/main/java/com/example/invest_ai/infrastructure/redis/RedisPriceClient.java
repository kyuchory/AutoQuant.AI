package com.example.invest_ai.infrastructure.redis;

import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infra.config.RedisKeys;
import com.example.invest_ai.infrastructure.kis.KisChartClient;
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
 * Writer: KisWebsocketClient (1단계 구현 완료), 본 클래스 (장마감/캐시 미스 시 KIS REST 폴백)
 * Reader: AssetSummaryService, 조건 매칭 워커
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPriceClient {

    private final StringRedisTemplate redisTemplate;
    private final KisChartClient kisChartClient;

    /**
     * 단일 종목 현재가 조회.
     * Redis miss 시 KIS REST(FHKST01010100)로 폴백하여 마지막 체결가(장마감 시 종가)를 조회하고,
     * 다음 실시간 틱이 올 때까지 사용할 수 있도록 Redis에 다시 저장한다.
     */
    public BigDecimal getCurrentPrice(String stockCode) {
        String key = RedisKeys.priceCurrent(stockCode);
        String value = redisTemplate.opsForValue().get(key);

        if (value != null && !value.isEmpty()) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                // 손상된 캐시 값 — 캐시 미스로 간주하고 KIS REST 폴백으로 복구한다.
                log.warn("Redis 캐시 값 파싱 실패, KIS REST 폴백으로 복구 시도: key={}, value={}", key, value);
            }
        }

        KisChartClient.CurrentQuote quote = kisChartClient.getCurrentQuote(stockCode);
        if (quote != null) {
            BigDecimal price = BigDecimal.valueOf(quote.price());
            redisTemplate.opsForValue().set(key, RedisKeys.formatPrice(price), RedisKeys.PRICE_CURRENT_TTL);
            setChangeRate(stockCode, quote.changeRate());
            return price;
        }

        throw new CustomException(ErrorCode.PRICE_UNAVAILABLE, "현재 시세를 조회할 수 없습니다. (종목: " + stockCode + ")");
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
            } catch (Exception e) {
                // CustomException(KIS 폴백 실패)뿐 아니라 예기치 못한 파싱/네트워크 예외도
                // 이 종목 하나만 0 처리하고 나머지 종목 조회는 계속 진행한다.
                log.warn("시세 조회 실패 (0 처리): stockCode={}, error={}", stockCode, e.getMessage());
                result.put(stockCode, BigDecimal.ZERO);
            }
        }
        return result;
    }
}