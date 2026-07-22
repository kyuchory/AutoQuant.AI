package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.entity.TradingHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TradingHistory Entity 상태 전이 단위 테스트 — 외부 의존성 없음
 */
class TradingHistoryTest {

    private static final BigDecimal PRICE = new BigDecimal("79500.0000");
    private static final int QUANTITY = 10;

    @Test
    @DisplayName("markFilled: status가 FILLED로 변경된다")
    void markFilled_status_FILLED변경() {
        // given
        TradingHistory history = TradingHistory.builder()
                .userId(1L).stockCode("005930").orderType("BUY").build();

        // when
        history.markFilled(PRICE, QUANTITY);

        // then
        assertThat(history.getStatus()).isEqualTo("FILLED");
    }

    @Test
    @DisplayName("markFilled: executionPrice, executionQuantity, totalAmount, executedAt이 세팅된다")
    void markFilled_모든필드_정상세팅() {
        // given
        TradingHistory history = TradingHistory.builder()
                .userId(1L).stockCode("005930").orderType("BUY").build();

        // when
        history.markFilled(PRICE, QUANTITY);

        // then
        assertThat(history.getExecutionPrice()).isEqualByComparingTo(PRICE);
        assertThat(history.getExecutionQuantity()).isEqualTo(QUANTITY);
        assertThat(history.getTotalAmount())
                .isEqualByComparingTo(PRICE.multiply(BigDecimal.valueOf(QUANTITY)));
        assertThat(history.getExecutedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFilled: totalAmount = price * quantity 계산이 정확하다")
    void markFilled_totalAmount_계산정확() {
        // given
        TradingHistory history = TradingHistory.builder()
                .userId(1L).stockCode("005930").orderType("BUY").build();
        BigDecimal expectedTotal = PRICE.multiply(BigDecimal.valueOf(QUANTITY));

        // when
        history.markFilled(PRICE, QUANTITY);

        // then
        assertThat(history.getTotalAmount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("markFailed: status가 FAILED로 변경된다")
    void markFailed_status_FAILED변경() {
        // given
        TradingHistory history = TradingHistory.builder()
                .userId(1L).stockCode("005930").orderType("BUY").build();

        // when
        history.markFailed("예수금 부족");

        // then
        assertThat(history.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("markFailed: failureReason이 정상 세팅된다")
    void markFailed_failureReason_정상세팅() {
        // given
        TradingHistory history = TradingHistory.builder()
                .userId(1L).stockCode("005930").orderType("BUY").build();
        String reason = "예수금 부족";

        // when
        history.markFailed(reason);

        // then
        assertThat(history.getFailureReason()).isEqualTo(reason);
    }
}