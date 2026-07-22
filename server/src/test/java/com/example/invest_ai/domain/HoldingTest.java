package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Holding Entity 단위 테스트 — 외부 의존성 없음
 */
class HoldingTest {

    private static final BigDecimal PRICE_10000 = new BigDecimal("10000.0000");
    private static final BigDecimal PRICE_20000 = new BigDecimal("20000.0000");

    @Test
    @DisplayName("increaseQuantity: 최초 매수 시 수량과 평단가가 정상 세팅된다")
    void increaseQuantity_최초매수_수량평단가세팅() {
        // given
        Holding holding = Holding.builder()
                .userId(1L).stockCode("005930")
                .quantity(10).averagePrice(PRICE_10000)
                .build();

        // when
        holding.increaseQuantity(5, PRICE_20000);

        // then — 평단가 = (10000*10 + 20000*5) / 15 = 13333.333...
        BigDecimal expected = new BigDecimal("10000.0000").multiply(BigDecimal.TEN)
                .add(PRICE_20000.multiply(BigDecimal.valueOf(5)))
                .divide(BigDecimal.valueOf(15), 4, RoundingMode.HALF_UP);

        assertThat(holding.getQuantity()).isEqualTo(15);
        assertThat(holding.getAveragePrice()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("increaseQuantity: 추가 매수 시 평단가 재계산이 정확하다")
    void increaseQuantity_추가매수_평단가재계산() {
        // given
        Holding holding = Holding.builder()
                .userId(1L).stockCode("005930")
                .quantity(10).averagePrice(PRICE_10000)
                .build();

        // when
        holding.increaseQuantity(5, PRICE_20000);

        // then: (10,000*10 + 20,000*5) / 15 = 13,333.3333
        BigDecimal totalCost = PRICE_10000.multiply(BigDecimal.TEN)
                .add(PRICE_20000.multiply(BigDecimal.valueOf(5)));
        BigDecimal expectedAvg = totalCost.divide(BigDecimal.valueOf(15), 4, RoundingMode.HALF_UP);

        assertThat(holding.getAveragePrice()).isEqualByComparingTo(expectedAvg);
        assertThat(holding.getQuantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("decreaseQuantity: 수량이 정상적으로 감소한다")
    void decreaseQuantity_정상매도_수량감소() {
        // given
        Holding holding = Holding.builder()
                .userId(1L).stockCode("005930")
                .quantity(10).averagePrice(PRICE_10000)
                .build();

        // when
        holding.decreaseQuantity(3);

        // then
        assertThat(holding.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("decreaseQuantity: 보유 수량보다 많은 수량 매도 시 CustomException 발생")
    void decreaseQuantity_수량부족_CustomException발생() {
        // given
        Holding holding = Holding.builder()
                .userId(1L).stockCode("005930")
                .quantity(10).averagePrice(PRICE_10000)
                .build();

        // when & then
        assertThatThrownBy(() -> holding.decreaseQuantity(20))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);
                });
    }

    @Test
    @DisplayName("decreaseQuantity: 보유 수량과 동일한 수량 매도 시 0이 된다 (경계값)")
    void decreaseQuantity_전량매도_0됨() {
        // given
        Holding holding = Holding.builder()
                .userId(1L).stockCode("005930")
                .quantity(10).averagePrice(PRICE_10000)
                .build();

        // when
        holding.decreaseQuantity(10);

        // then
        assertThat(holding.getQuantity()).isZero();
    }
}