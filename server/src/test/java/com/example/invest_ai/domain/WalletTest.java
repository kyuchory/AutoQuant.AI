package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wallet Entity 단위 테스트 — 외부 의존성 없음
 */
class WalletTest {

    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("10000000.0000");
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("500000.0000");
    private static final BigDecimal WITHDRAW_AMOUNT = new BigDecimal("300000.0000");
    private static final BigDecimal OVER_AMOUNT = new BigDecimal("20000000.0000");

    @Test
    @DisplayName("deposit: 잔고가 정상적으로 증가한다")
    void deposit_정상입금_잔고증가() {
        // given
        Wallet wallet = Wallet.builder().userId(1L).balance(INITIAL_BALANCE).build();

        // when
        wallet.deposit(DEPOSIT_AMOUNT);

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo(
                INITIAL_BALANCE.add(DEPOSIT_AMOUNT));
    }

    @Test
    @DisplayName("withdraw: 잔고가 정상적으로 감소한다")
    void withdraw_정상출금_잔고감소() {
        // given
        Wallet wallet = Wallet.builder().userId(1L).balance(INITIAL_BALANCE).build();

        // when
        wallet.withdraw(WITHDRAW_AMOUNT);

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo(
                INITIAL_BALANCE.subtract(WITHDRAW_AMOUNT));
    }

    @Test
    @DisplayName("withdraw: 잔고보다 큰 금액 출금 시 CustomException 발생")
    void withdraw_잔고부족_CustomException발생() {
        // given
        Wallet wallet = Wallet.builder().userId(1L).balance(INITIAL_BALANCE).build();

        // when & then
        assertThatThrownBy(() -> wallet.withdraw(OVER_AMOUNT))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    CustomException ce = (CustomException) e;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
                });
    }

    @Test
    @DisplayName("withdraw: 잔고와 동일한 금액 출금 시 성공 (경계값)")
    void withdraw_잔고전액출금_성공() {
        // given
        Wallet wallet = Wallet.builder().userId(1L).balance(INITIAL_BALANCE).build();

        // when
        wallet.withdraw(INITIAL_BALANCE);

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}