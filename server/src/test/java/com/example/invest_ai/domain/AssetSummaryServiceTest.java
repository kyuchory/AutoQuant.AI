package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.asset.service.AssetSummaryService;
import com.example.invest_ai.domain.asset.service.HoldingService;
import com.example.invest_ai.domain.asset.service.WalletService;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AssetSummaryServiceTest {

    private static final Long USER_ID = 1L;
    private static final String STOCK_CODE = "005930";
    private static final BigDecimal BALANCE = new BigDecimal("10000000.0000");
    private static final BigDecimal CURRENT_PRICE = new BigDecimal("79500.0000");
    private static final BigDecimal AVG_PRICE = new BigDecimal("75000.0000");

    @Mock private WalletService walletService;
    @Mock private HoldingService holdingService;
    @Mock private StockRepository stockRepository;
    @Mock private RedisPriceClient redisPriceClient;
    @InjectMocks private AssetSummaryService assetSummaryService;

    @Test
    @DisplayName("getAssetSummary: 정상 응답 반환")
    void getAssetSummary_정상_응답반환() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        Holding holding = Holding.builder().userId(USER_ID).stockCode(STOCK_CODE).quantity(10).averagePrice(AVG_PRICE).build();
        Stock stock = Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(holdingService.findAllByUserId(USER_ID)).willReturn(List.of(holding));
        given(redisPriceClient.getAllCurrentPrices(List.of(STOCK_CODE))).willReturn(Map.of(STOCK_CODE, CURRENT_PRICE));
        given(stockRepository.findAllById(List.of(STOCK_CODE))).willReturn(List.of(stock));
        AssetSummaryResponse result = assetSummaryService.getAssetSummary(USER_ID);
        assertThat(result.walletBalance()).isEqualByComparingTo(BALANCE);
        assertThat(result.holdings()).hasSize(1);
    }

    @Test
    @DisplayName("getAssetSummary: Holdings가 비어있으면 빈 리스트 반환")
    void getAssetSummary_보유종목없음_빈리스트() {
        Wallet wallet = Wallet.builder().userId(USER_ID).balance(BALANCE).build();
        given(walletService.findByUserId(USER_ID)).willReturn(wallet);
        given(holdingService.findAllByUserId(USER_ID)).willReturn(List.of());
        AssetSummaryResponse result = assetSummaryService.getAssetSummary(USER_ID);
        assertThat(result.totalEvaluationAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.holdings()).isEmpty();
    }

    @Test
    @DisplayName("getAssetSummary: Wallet이 없으면 E4041 발생")
    void getAssetSummary_지갑없음_E4041발생() {
        given(walletService.findByUserId(USER_ID)).willThrow(new CustomException(ErrorCode.ASSET_NOT_FOUND));
        assertThatThrownBy(() -> assetSummaryService.getAssetSummary(USER_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException)e).getErrorCode()).isEqualTo(ErrorCode.ASSET_NOT_FOUND));
    }
}