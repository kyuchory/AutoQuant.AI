package com.example.invest_ai.infra.scheduler;

import com.example.invest_ai.config.RabbitMqConfig;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.infra.naver.NaverNewsClient;
import com.example.invest_ai.domain.report.dto.NaverNewsResponse;
import com.example.invest_ai.infra.rabbitmq.NewsAnalysisMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 네이버 뉴스 수집 스케줄러 (workflow.md §6)
 *
 * 5분 주기로 stocks.is_monitored = TRUE인 종목명을 키워드로
 * 네이버 뉴스 검색 API를 호출하고, 신규 뉴스를 RabbitMQ news-queue에 발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverNewsScheduler {

    private final StockRepository stockRepository;
    private final NaverNewsClient naverNewsClient;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 300_000) // 5분
    public void fetchNaverNews() {
        List<Stock> monitoredStocks = stockRepository.findAllByIsMonitoredTrue();

        if (monitoredStocks.isEmpty()) {
            log.warn("[뉴스 스케줄러] 모니터링 대상 종목이 없습니다.");
            return;
        }

        log.info("[뉴스 스케줄러] {}개 종목 뉴스 수집 시작", monitoredStocks.size());

        for (Stock stock : monitoredStocks) {
            try {
                List<NaverNewsResponse.Item> items = naverNewsClient.fetchNews(stock.getStockName());

                for (NaverNewsResponse.Item item : items) {
                    String routingKey = "news." + stock.getStockCode();
                    NewsAnalysisMessage message = new NewsAnalysisMessage(
                            stock.getStockCode(), item);
                    rabbitTemplate.convertAndSend(
                            RabbitMqConfig.EXCHANGE_NAME,
                            routingKey,
                            message);
                    log.debug("[뉴스 스케줄러] MQ 발행: stock={} title={}", stock.getStockCode(), item.getStrippedTitle());
                }
            } catch (Exception e) {
                log.error("[뉴스 스케줄러] 종목 {} 수집 중 예외: {}", stock.getStockCode(), e.getMessage());
            }
        }

        log.info("[뉴스 스케줄러] 뉴스 수집 완료");
    }
}
