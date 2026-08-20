package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.entity.TradingHistory;
import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.asset.service.AssetService;
import com.example.invest_ai.domain.asset.service.HoldingService;
import com.example.invest_ai.domain.asset.service.OrderSettlementService;
import com.example.invest_ai.domain.asset.service.WalletService;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.global.websocket.WebSocketSessionManager;
import com.example.invest_ai.infrastructure.kis.KisOrderClient;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AssetService는 이제 오케스트레이션(체결가 확정 → 사전검증 → KIS 호출)만 담당하고,
 * 실제 지갑/보유량 DB 반영은 OrderSettlementService(REQUIRES_NEW)에 위임한다.
 * 그 위임 로직 자체의 검증(increaseQuantity, decreaseQuantity 등)은 OrderSettlementServiceTest에서 수행한다.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long HISTORY_ID = 100L;
    private static final String STOCK_CODE = "005930";
    private static final BigDecimal BALANCE = new BigDecimal("10000000.0000");
    private static final BigDecimal CURRENT_PRICE = new BigDecimal("79500.0000");
    private static final BigDecimal LIMIT_PRICE = new BigDecimal("78000.0000");

    @Mock private WalletService walletService;
    @Mock private HoldingService holdingService;
    @Mock private StockRepository stockRepository;
    @Mock private RedisPriceClient redisPriceClient;
    @Mock private KisOrderClient kisOrderClient;
    @Mock private OrderSettlementService orderSettlementService;
    @Mock private WebSocketSessionManager webSocketSessionManager;
    @InjectMocks private AssetService assetService;

    private Stock stock() {
        return Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
    }

    private TradingHistory pendingHistory(Long conditionId) {
        return TradingHistory.builder()
                .userId(USER_ID).conditionId(conditionId).stockCode(STOCK_CODE).orderType("BUY").build();
    }

    @Test
    @DisplayName("executeOrder_BUY(MARKET): 실시간 시장가로 체결가 확정 후 정산 위임")
    void executeOrder_BUY_MARKET_현재가로_정산위임() {
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 3, "01", null);
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        TradingHistory pending = pendingHistory(null);
        TradingHistory filled = pendingHistory(null);
        filled.markFilled(CURRENT_PRICE, 3);

        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(orderSettlementService.createPendingHistory(USER_ID, null, STOCK_CODE, "BUY")).willReturn(pending);
        given(orderSettlementService.settleFilledOrder(USER_ID, pending.getHistoryId(), "BUY", STOCK_CODE, 3, CURRENT_PRICE))
                .willReturn(filled);

        OrderResponse result = assetService.executeOrder(USER_ID, request);

        assertThat(result.status()).isEqualTo("FILLED");
        verify(orderSettlementService).settleFilledOrder(USER_ID, pending.getHistoryId(), "BUY", STOCK_CODE, 3, CURRENT_PRICE);
    }

    @Test
    @DisplayName("executeOrder_BUY(LIMIT): 지정가로 체결가를 확정한다 (시장가로 새는 회귀 방지)")
    void executeOrder_BUY_LIMIT_지정가로_체결가확정() {
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 3, "00", LIMIT_PRICE);
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        TradingHistory pending = pendingHistory(null);
        TradingHistory filled = pendingHistory(null);
        filled.markFilled(LIMIT_PRICE, 3);

        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(orderSettlementService.createPendingHistory(USER_ID, null, STOCK_CODE, "BUY")).willReturn(pending);
        given(orderSettlementService.settleFilledOrder(USER_ID, pending.getHistoryId(), "BUY", STOCK_CODE, 3, LIMIT_PRICE))
                .willReturn(filled);

        assetService.executeOrder(USER_ID, request);

        // LIMIT 주문은 redisPriceClient(시장가)를 조회하지 않고 지정가를 그대로 체결가로 써야 한다.
        verify(redisPriceClient, never()).getCurrentPrice(anyString());
        verify(orderSettlementService).settleFilledOrder(USER_ID, pending.getHistoryId(), "BUY", STOCK_CODE, 3, LIMIT_PRICE);
    }

    @Test
    @DisplayName("executeOrder_BUY(LIMIT): price 없이 요청하면 즉시 거부")
    void executeOrder_BUY_LIMIT_가격누락_예외() {
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 3, "00", null);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));

        assertThatThrownBy(() -> assetService.executeOrder(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(orderSettlementService, never()).createPendingHistory(any(), any(), any(), any());
    }

    @Test
    @DisplayName("executeOrder_BUY: 잔고 부족 시 사전검증에서 즉시 거부 (PENDING 이력조차 생성 안 함)")
    void executeOrder_BUY_잔고부족_사전검증에서_거부() {
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 10, "01", null);
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(new BigDecimal("100.0000")).build();

        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);

        assertThatThrownBy(() -> assetService.executeOrder(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
        verify(orderSettlementService, never()).createPendingHistory(any(), any(), any(), any());
        verify(kisOrderClient, never()).executeOrder(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("executeOrder_SELL: 미보유 종목 매도 시 CustomException 전파")
    void executeOrder_SELL_미보유종목_HOLDING_NOT_FOUND() {
        OrderRequest request = new OrderRequest(STOCK_CODE, "SELL", 3, "01", null);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(holdingService.findOrThrow(USER_ID, STOCK_CODE))
                .willThrow(new CustomException(ErrorCode.HOLDING_NOT_FOUND));

        assertThatThrownBy(() -> assetService.executeOrder(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.HOLDING_NOT_FOUND));
    }

    @Test
    @DisplayName("executeOrder: KIS 주문 API 실패 시 markFailed로 위임하고 settleFilledOrder는 호출하지 않는다")
    void executeOrder_KIS실패_markFailed위임() {
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 3, "01", null);
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        TradingHistory pending = pendingHistory(null);
        TradingHistory failed = pendingHistory(null);
        failed.markFailed("KIS 서버 오류");

        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(orderSettlementService.createPendingHistory(USER_ID, null, STOCK_CODE, "BUY")).willReturn(pending);
        org.mockito.Mockito.doThrow(new RuntimeException("KIS 서버 오류"))
                .when(kisOrderClient).executeOrder(eq(STOCK_CODE), eq("BUY"), eq(3), eq("01"), isNull());
        given(orderSettlementService.markFailed(eq(pending.getHistoryId()), anyString())).willReturn(failed);

        OrderResponse result = assetService.executeOrder(USER_ID, request);

        assertThat(result.status()).isEqualTo("FAILED");
        verify(orderSettlementService, never()).settleFilledOrder(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("executeOrder(3-arg): ConditionMatchingEngine 내부 호출은 conditionId를 이력 생성에 전달한다")
    void executeOrder_conditionId_전달() {
        Long conditionId = 42L;
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 3, "01", null);
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        TradingHistory pending = pendingHistory(conditionId);
        TradingHistory filled = pendingHistory(conditionId);
        filled.markFilled(CURRENT_PRICE, 3);

        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(orderSettlementService.createPendingHistory(USER_ID, conditionId, STOCK_CODE, "BUY")).willReturn(pending);
        given(orderSettlementService.settleFilledOrder(USER_ID, pending.getHistoryId(), "BUY", STOCK_CODE, 3, CURRENT_PRICE))
                .willReturn(filled);

        assetService.executeOrder(USER_ID, request, conditionId);

        verify(orderSettlementService).createPendingHistory(USER_ID, conditionId, STOCK_CODE, "BUY");
    }

    @Test
    @DisplayName("executeOrder(2-arg): 컨트롤러 경로(수동 주문)는 항상 conditionId=null로 고정한다")
    void executeOrder_수동주문_conditionId항상null() {
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 3, "01", null);
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        TradingHistory pending = pendingHistory(null);
        TradingHistory filled = pendingHistory(null);
        filled.markFilled(CURRENT_PRICE, 3);

        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(orderSettlementService.createPendingHistory(USER_ID, null, STOCK_CODE, "BUY")).willReturn(pending);
        given(orderSettlementService.settleFilledOrder(USER_ID, pending.getHistoryId(), "BUY", STOCK_CODE, 3, CURRENT_PRICE))
                .willReturn(filled);

        assetService.executeOrder(USER_ID, request);

        verify(orderSettlementService).createPendingHistory(USER_ID, null, STOCK_CODE, "BUY");
    }
}
