package com.example.invest_ai.infra.scheduler;

import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.infra.config.RedisKeys;
import com.example.invest_ai.infrastructure.kis.KisChartClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * 장 시작 직전 초기 시세 스냅샷 예열 스케줄러 (docs/kisflow.md §5-②)
 *
 * 09:00 KST에 감시 종목(is_monitored=TRUE)을 KIS REST(FHKST01010100)로 한 번씩 조회해
 * Redis price:{stockCode}:current 캐시를 미리 채워둔다. KIS 웹소켓 체결 틱이 아직 안 들어온
 * 개장 직후 구간에도 사용자가 즉시 최신 현재가를 볼 수 있도록 하기 위함이며, 이 스케줄이 없어도
 * RedisPriceClient의 캐시 미스 폴백으로 동작은 하지만 그 경우 최초 요청자가 지연을 떠안게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisSnapshotScheduler {

    private final StockRepository stockRepository;
    private final KisChartClient kisChartClient;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    public void warmUpOpeningSnapshot() {
        List<Stock> stocks = stockRepository.findAllByIsMonitoredTrue();
        if (stocks.isEmpty()) {
            log.warn("[장시작 스냅샷] 모니터링 대상 종목이 없습니다.");
            return;
        }

        log.info("[장시작 스냅샷] {}개 종목 초기 시세 예열 시작", stocks.size());

        for (Stock stock : stocks) {
            try {
                KisChartClient.CurrentQuote quote = kisChartClient.getCurrentQuote(stock.getStockCode());
                if (quote == null) {
                    log.warn("[장시작 스냅샷] 종목 {} 조회 결과 없음", stock.getStockCode());
                    continue;
                }
                BigDecimal price = BigDecimal.valueOf(quote.price());
                redisTemplate.opsForValue().set(
                        RedisKeys.priceCurrent(stock.getStockCode()),
                        RedisKeys.formatPrice(price),
                        RedisKeys.PRICE_CURRENT_TTL);
                redisTemplate.opsForValue().set(
                        RedisKeys.priceChangeRate(stock.getStockCode()),
                        String.valueOf(quote.changeRate()),
                        Duration.ofHours(24));
            } catch (Exception e) {
                log.warn("[장시작 스냅샷] 종목 {} 예열 실패: {}", stock.getStockCode(), e.getMessage());
            }
        }

        log.info("[장시작 스냅샷] 완료");
    }
}
