package com.example.invest_ai.infra;

import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infra.config.RedisKeys;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * RedisPriceClient 단위 테스트 — RedisTemplate 목킹
 */
@ExtendWith(MockitoExtension.class)
class RedisPriceClientTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisPriceClient redisPriceClient;

    @Test
    @DisplayName("getCurrentPrice: Redis에 값이 있으면 BigDecimal로 정상 변환한다")
    void getCurrentPrice_값있음_BigDecimal변환() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("79500.0000");

        // when
        BigDecimal price = redisPriceClient.getCurrentPrice("005930");

        // then
        assertThat(price).isEqualByComparingTo(new BigDecimal("79500.0000"));
    }

    @Test
    @DisplayName("getCurrentPrice: Redis에 값이 없으면 CustomException 발생")
    void getCurrentPrice_값없음_CustomException발생() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        // when & then
        assertThatThrownBy(() -> redisPriceClient.getCurrentPrice("005930"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.PRICE_UNAVAILABLE);
                });
    }

    @Test
    @DisplayName("getAllCurrentPrices: 여러 종목 코드를 받아 Map으로 반환한다")
    void getAllCurrentPrices_여러종목_Map반환() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(RedisKeys.priceCurrent("005930"))).willReturn("79500.0000");
        given(valueOperations.get(RedisKeys.priceCurrent("000660"))).willReturn("120000.0000");

        // when
        Map<String, BigDecimal> prices = redisPriceClient.getAllCurrentPrices(
                List.of("005930", "000660"));

        // then
        assertThat(prices).hasSize(2);
        assertThat(prices.get("005930")).isEqualByComparingTo(new BigDecimal("79500.0000"));
        assertThat(prices.get("000660")).isEqualByComparingTo(new BigDecimal("120000.0000"));
    }

    @Test
    @DisplayName("getAllCurrentPrices: Redis 값 없으면 BigDecimal.ZERO로 처리")
    void getAllCurrentPrices_일부종목없음_ZERO처리() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(RedisKeys.priceCurrent("005930"))).willReturn("79500.0000");
        given(valueOperations.get(RedisKeys.priceCurrent("000660"))).willReturn(null);

        // when
        Map<String, BigDecimal> prices = redisPriceClient.getAllCurrentPrices(
                List.of("005930", "000660"));

        // then
        assertThat(prices.get("005930")).isEqualByComparingTo(new BigDecimal("79500.0000"));
        assertThat(prices.get("000660")).isEqualByComparingTo(BigDecimal.ZERO);
    }
}