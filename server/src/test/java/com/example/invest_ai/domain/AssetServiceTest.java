package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.asset.repository.TradingHistoryRepository;
import com.example.invest_ai.domain.asset.service.AssetService;
import com.example.invest_ai.domain.asset.service.HoldingService;
import com.example.invest_ai.domain.asset.service.WalletService;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    private static final Long USER_ID = 1L;
    private static final String STOCK_CODE = "005930";
    private static final BigDecimal BALANCE = new BigDecimal("10000000.0000");
    private static final BigDecimal CURRENT_PRICE = new BigDecimal("79500.0000");
    private static final BigDecimal AVG_PRICE = new BigDecimal("75000.0000");

    @Mock private WalletService walletService;
    @Mock private HoldingService holdingService;
    @Mock private TradingHistoryRepository tradingHistoryRepository;
    @Mock private StockRepository stockRepository;
    @Mock private RedisPriceClient redisPriceClient;
    @Mock private KisOrderClient kisOrderClient;
    @InjectMocks private AssetService assetService;

    @Test
    @DisplayName("executeOrder_BUY: 정상 매수 시 Holding 생성")
    void executeOrder_BUY_정상매수_Holding생성() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Stock stock = Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 3);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(holdingService.findByUserIdAndStockCode(USER_ID, STOCK_CODE)).willReturn(Optional.empty());
        OrderResponse result = assetService.executeOrder(USER_ID, request);
        assertThat(result.status()).isEqualTo("FILLED");
        verify(walletService).save(any(Wallet.class));
        verify(holdingService).save(any(Holding.class));
    }

    @Test
    @DisplayName("executeOrder_BUY: 기존 종목 추가 매수 시 increaseQuantity 호출")
    void executeOrder_BUY_추가매수_increaseQuantity호출() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Stock stock = Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
        Holding existing = Holding.builder().userId(USER_ID).stockCode(STOCK_CODE).quantity(5).averagePrice(AVG_PRICE).build();
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 3);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(holdingService.findByUserIdAndStockCode(USER_ID, STOCK_CODE)).willReturn(Optional.of(existing));
        assetService.executeOrder(USER_ID, request);
        assertThat(existing.getQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("executeOrder_BUY: 잔고 부족 시 CustomException 전파")
    void executeOrder_BUY_잔고부족_CustomException발생() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(new BigDecimal("100.0000")).build();
        Stock stock = Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
        OrderRequest request = new OrderRequest(STOCK_CODE, "BUY", 10);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        assertThatThrownBy(() -> assetService.executeOrder(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    @DisplayName("executeOrder_SELL: 정상 매도 시 decreaseQuantity + deposit 호출")
    void executeOrder_SELL_정상매도_Holding감소() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Stock stock = Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
        Holding holding = Holding.builder().userId(USER_ID).stockCode(STOCK_CODE).quantity(10).averagePrice(AVG_PRICE).build();
        OrderRequest request = new OrderRequest(STOCK_CODE, "SELL", 3);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(holdingService.findOrThrow(USER_ID, STOCK_CODE)).willReturn(holding);
        assetService.executeOrder(USER_ID, request);
        assertThat(holding.getQuantity()).isEqualTo(7);
        verify(walletService).save(any(Wallet.class));
    }

    @Test
    @DisplayName("executeOrder_SELL: 전량 매도 시 Holding 삭제")
    void executeOrder_SELL_전량매도_Holding삭제() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Stock stock = Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
        Holding holding = Holding.builder().userId(USER_ID).stockCode(STOCK_CODE).quantity(5).averagePrice(AVG_PRICE).build();
        OrderRequest request = new OrderRequest(STOCK_CODE, "SELL", 5);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(holdingService.findOrThrow(USER_ID, STOCK_CODE)).willReturn(holding);
        assetService.executeOrder(USER_ID, request);
        verify(holdingService).delete(holding);
    }

    @Test
    @DisplayName("executeOrder_SELL: 미보유 종목 매도 시 CustomException")
    void executeOrder_SELL_미보유종목_HOLDING_NOT_FOUND() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Stock stock = Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
        OrderRequest request = new OrderRequest(STOCK_CODE, "SELL", 3);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock));
        given(redisPriceClient.getCurrentPrice(STOCK_CODE)).willReturn(CURRENT_PRICE);
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(holdingService.findOrThrow(USER_ID, STOCK_CODE))
                .willThrow(new CustomException(ErrorCode.HOLDING_NOT_FOUND));
        assertThatThrownBy(() -> assetService.executeOrder(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.HOLDING_NOT_FOUND));
    }
}