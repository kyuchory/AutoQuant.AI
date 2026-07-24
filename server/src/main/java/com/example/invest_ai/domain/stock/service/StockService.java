package com.example.invest_ai.domain.stock.service;

import com.example.invest_ai.domain.stock.dto.StockDto.StockInfo;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.infrastructure.kis.KisChartClient;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 종목 조회 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final RedisPriceClient redisPriceClient;
    private final KisChartClient kisChartClient;

    /** 모니터링 대상 종목 목록 + Redis 현재가 + 전일대비 등락률 */
    public List<StockInfo> getMonitoredStocksWithPrice() {
        List<Stock> stocks = stockRepository.findAllByIsMonitoredTrue();

        return stocks.stream().map(stock -> {
            BigDecimal currentPrice;
            try {
                currentPrice = redisPriceClient.getCurrentPrice(stock.getStockCode());
            } catch (Exception e) {
                log.warn("시세 조회 실패: stockCode={}", stock.getStockCode());
                currentPrice = BigDecimal.ZERO;
            }

            // ① Redis에서 전일대비 등락률 조회
            Double changeRate = redisPriceClient.getChangeRate(stock.getStockCode());

            // ② Redis miss → KIS REST API(FHKST01010100) fallback
            if (changeRate == null) {
                try {
                    KisChartClient.CurrentQuote quote = kisChartClient.getCurrentQuote(stock.getStockCode());
                    if (quote != null) {
                        changeRate = quote.changeRate();
                        redisPriceClient.setChangeRate(stock.getStockCode(), changeRate);
                        log.debug("KIS fallback: stockCode={}, price={}, changeRate={}",
                                stock.getStockCode(), quote.price(), changeRate);
                    }
                } catch (Exception ex) {
                    log.warn("KIS 현재가 조회 실패: stockCode={}", stock.getStockCode());
                }
            }

            double rate = changeRate != null ? changeRate : 0.0;
            return new StockInfo(stock.getStockCode(), stock.getStockName(), currentPrice, rate);
        }).collect(Collectors.toList());
    }
}
