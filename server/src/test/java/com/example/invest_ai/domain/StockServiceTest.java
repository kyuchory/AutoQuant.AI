package com.example.invest_ai.domain;

import com.example.invest_ai.domain.stock.dto.StockDto.StockInfo;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.domain.stock.service.StockService;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * StockService 단위 테스트 — Mockito 목킹
 */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private RedisPriceClient redisPriceClient;

    @InjectMocks
    private StockService stockService;

    @Test
    @DisplayName("getMonitoredStocksWithPrice: is_monitored=true 종목만 조회한다")
    void getMonitoredStocksWithPrice_모니터링종목만조회() {
        // given
        Stock samsung = Stock.builder().stockCode("005930").stockName("삼성전자").isMonitored(true).build();
        Stock sk = Stock.builder().stockCode("000660").stockName("SK하이닉스").isMonitored(true).build();
        Stock excluded = Stock.builder().stockCode("999999").stockName("제외종목").isMonitored(false).build();

        given(stockRepository.findAllByIsMonitoredTrue()).willReturn(List.of(samsung, sk));
        given(redisPriceClient.getCurrentPrice("005930")).willReturn(new BigDecimal("79500.0000"));
        given(redisPriceClient.getCurrentPrice("000660")).willReturn(new BigDecimal("120000.0000"));

        // when
        List<StockInfo> result = stockService.getMonitoredStocksWithPrice();

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(StockInfo::stockCode)
                .containsExactly("005930", "000660");
    }

    @Test
    @DisplayName("getMonitoredStocksWithPrice: 각 종목의 currentPrice가 Redis에서 조회된다")
    void getMonitoredStocksWithPrice_현재가_Redis조회() {
        // given
        Stock stock = Stock.builder().stockCode("005930").stockName("삼성전자").isMonitored(true).build();
        given(stockRepository.findAllByIsMonitoredTrue()).willReturn(List.of(stock));
        given(redisPriceClient.getCurrentPrice("005930")).willReturn(new BigDecimal("79500.0000"));

        // when
        List<StockInfo> result = stockService.getMonitoredStocksWithPrice();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPrice()).isEqualByComparingTo(new BigDecimal("79500.0000"));
    }

    @Test
    @DisplayName("getMonitoredStocksWithPrice: Redis 시세 조회 실패 종목은 currentPrice=0으로 처리")
    void getMonitoredStocksWithPrice_Redis실패_0처리() {
        // given
        Stock stock = Stock.builder().stockCode("005930").stockName("삼성전자").isMonitored(true).build();
        given(stockRepository.findAllByIsMonitoredTrue()).willReturn(List.of(stock));
        given(redisPriceClient.getCurrentPrice("005930"))
                .willThrow(new RuntimeException("Redis unavailable"));

        // when
        List<StockInfo> result = stockService.getMonitoredStocksWithPrice();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}