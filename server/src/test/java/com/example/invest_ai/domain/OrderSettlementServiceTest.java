package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.entity.TradingHistory;
import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.asset.repository.TradingHistoryRepository;
import com.example.invest_ai.domain.asset.service.HoldingService;
import com.example.invest_ai.domain.asset.service.OrderSettlementService;
import com.example.invest_ai.domain.asset.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 체결 성공 후 지갑 락 보유 구간에서 이뤄지는 DB 정산(잔고/보유량 반영)만 검증한다.
 * KIS 호출/사전검증 오케스트레이션은 AssetServiceTest에서 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class OrderSettlementServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long HISTORY_ID = 100L;
    private static final String STOCK_CODE = "005930";
    private static final BigDecimal BALANCE = new BigDecimal("10000000.0000");
    private static final BigDecimal EXECUTION_PRICE = new BigDecimal("79500.0000");
    private static final BigDecimal AVG_PRICE = new BigDecimal("75000.0000");

    @Mock private WalletService walletService;
    @Mock private HoldingService holdingService;
    @Mock private TradingHistoryRepository tradingHistoryRepository;
    @InjectMocks private OrderSettlementService orderSettlementService;

    private TradingHistory pendingHistory(String orderType) {
        return TradingHistory.builder().userId(USER_ID).stockCode(STOCK_CODE).orderType(orderType).build();
    }

    @Test
    @DisplayName("createPendingHistory: PENDING 상태로 저장한다")
    void createPendingHistory_PENDING상태로_저장() {
        TradingHistory saved = pendingHistory("BUY");
        given(tradingHistoryRepository.save(any(TradingHistory.class))).willReturn(saved);

        TradingHistory result = orderSettlementService.createPendingHistory(USER_ID, null, STOCK_CODE, "BUY");

        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("markFailed: 이력을 FAILED로 마감한다")
    void markFailed_FAILED로_마감() {
        TradingHistory history = pendingHistory("BUY");
        given(tradingHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));
        given(tradingHistoryRepository.save(any(TradingHistory.class))).willReturn(history);

        TradingHistory result = orderSettlementService.markFailed(HISTORY_ID, "KIS 서버 오류");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getFailureReason()).isEqualTo("KIS 서버 오류");
    }

    @Test
    @DisplayName("settleFilledOrder_BUY: 신규 종목이면 Holding을 새로 생성한다")
    void settleFilledOrder_BUY_신규종목_Holding생성() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        TradingHistory history = pendingHistory("BUY");

        given(tradingHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));
        given(walletService.findByUserIdForUpdate(USER_ID)).willReturn(wallet);
        given(holdingService.findByUserIdAndStockCode(USER_ID, STOCK_CODE)).willReturn(Optional.empty());
        given(tradingHistoryRepository.save(any(TradingHistory.class))).willReturn(history);

        TradingHistory result = orderSettlementService.settleFilledOrder(
                USER_ID, HISTORY_ID, "BUY", STOCK_CODE, 3, EXECUTION_PRICE);

        assertThat(result.getStatus()).isEqualTo("FILLED");
        assertThat(wallet.getBalance()).isEqualTo(BALANCE.subtract(EXECUTION_PRICE.multiply(BigDecimal.valueOf(3))));
        verify(holdingService).save(any(Holding.class));
        verify(walletService).save(wallet);
    }

    @Test
    @DisplayName("settleFilledOrder_BUY: 기존 보유 종목이면 increaseQuantity로 평단가를 재계산한다")
    void settleFilledOrder_BUY_기존종목_평단가재계산() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Holding existing = Holding.builder().userId(USER_ID).stockCode(STOCK_CODE).quantity(5).averagePrice(AVG_PRICE).build();
        TradingHistory history = pendingHistory("BUY");

        given(tradingHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));
        given(walletService.findByUserIdForUpdate(USER_ID)).willReturn(wallet);
        given(holdingService.findByUserIdAndStockCode(USER_ID, STOCK_CODE)).willReturn(Optional.of(existing));
        given(tradingHistoryRepository.save(any(TradingHistory.class))).willReturn(history);

        orderSettlementService.settleFilledOrder(USER_ID, HISTORY_ID, "BUY", STOCK_CODE, 3, EXECUTION_PRICE);

        assertThat(existing.getQuantity()).isEqualTo(8);
        verify(holdingService).save(existing);
    }

    @Test
    @DisplayName("settleFilledOrder_SELL: 일부 매도 시 decreaseQuantity + deposit")
    void settleFilledOrder_SELL_일부매도_수량감소() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Holding holding = Holding.builder().userId(USER_ID).stockCode(STOCK_CODE).quantity(10).averagePrice(AVG_PRICE).build();
        TradingHistory history = pendingHistory("SELL");

        given(tradingHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));
        given(walletService.findByUserIdForUpdate(USER_ID)).willReturn(wallet);
        given(holdingService.findOrThrow(USER_ID, STOCK_CODE)).willReturn(holding);
        given(tradingHistoryRepository.save(any(TradingHistory.class))).willReturn(history);

        orderSettlementService.settleFilledOrder(USER_ID, HISTORY_ID, "SELL", STOCK_CODE, 3, EXECUTION_PRICE);

        assertThat(holding.getQuantity()).isEqualTo(7);
        assertThat(wallet.getBalance()).isEqualTo(BALANCE.add(EXECUTION_PRICE.multiply(BigDecimal.valueOf(3))));
        verify(holdingService).save(holding);
    }

    @Test
    @DisplayName("settleFilledOrder_SELL: 전량 매도 시 Holding을 삭제한다")
    void settleFilledOrder_SELL_전량매도_Holding삭제() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Holding holding = Holding.builder().userId(USER_ID).stockCode(STOCK_CODE).quantity(5).averagePrice(AVG_PRICE).build();
        TradingHistory history = pendingHistory("SELL");

        given(tradingHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));
        given(walletService.findByUserIdForUpdate(USER_ID)).willReturn(wallet);
        given(holdingService.findOrThrow(USER_ID, STOCK_CODE)).willReturn(holding);
        given(tradingHistoryRepository.save(any(TradingHistory.class))).willReturn(history);

        orderSettlementService.settleFilledOrder(USER_ID, HISTORY_ID, "SELL", STOCK_CODE, 5, EXECUTION_PRICE);

        verify(holdingService).delete(holding);
    }

    @Test
    @DisplayName("settleFilledOrder: KIS는 이미 체결됐지만 락 이후 잔고가 부족하면(동시성 경합) FAILED로 마감하고 예외를 던지지 않는다")
    void settleFilledOrder_동시성경합_잔고부족시_FAILED마감() {
        // 사전검증(preCheckBalance) 이후 다른 스레드가 잔고를 먼저 소진한 극단적 경합 상황을 재현
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(new BigDecimal("100.0000")).build();
        TradingHistory history = pendingHistory("BUY");

        given(tradingHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));
        given(walletService.findByUserIdForUpdate(USER_ID)).willReturn(wallet);
        given(tradingHistoryRepository.save(any(TradingHistory.class))).willReturn(history);

        TradingHistory result = orderSettlementService.settleFilledOrder(
                USER_ID, HISTORY_ID, "BUY", STOCK_CODE, 3, EXECUTION_PRICE);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getFailureReason()).contains("정산 실패");
        verify(walletService, org.mockito.Mockito.never()).save(any(Wallet.class));
    }

    @Test
    void settleFilledOrder_동시성경합_잔고부족시_예외를_던지지않는다() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(new BigDecimal("100.0000")).build();
        TradingHistory history = pendingHistory("BUY");

        given(tradingHistoryRepository.findById(HISTORY_ID)).willReturn(Optional.of(history));
        given(walletService.findByUserIdForUpdate(USER_ID)).willReturn(wallet);
        given(tradingHistoryRepository.save(any(TradingHistory.class))).willReturn(history);

        org.assertj.core.api.Assertions.assertThatCode(() ->
                orderSettlementService.settleFilledOrder(USER_ID, HISTORY_ID, "BUY", STOCK_CODE, 3, EXECUTION_PRICE)
        ).doesNotThrowAnyException();
    }
}
