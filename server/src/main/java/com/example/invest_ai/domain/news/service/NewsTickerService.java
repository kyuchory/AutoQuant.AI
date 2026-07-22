package com.example.invest_ai.domain.news.service;

import com.example.invest_ai.domain.news.dto.NewsTickerDto;
import com.example.invest_ai.domain.news.entity.NewsSentiment;
import com.example.invest_ai.domain.news.repository.NewsSentimentRepository;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsTickerService {

    private final StockRepository stockRepository;
    private final NewsSentimentRepository newsSentimentRepository;

    /**
     * is_monitored = TRUE인 모든 종목의 최신 뉴스 1건씩 반환한다.
     */
    public List<NewsTickerDto> getTickerNews() {
        List<Stock> stocks = stockRepository.findAllByIsMonitoredTrue();
        List<NewsTickerDto> result = new ArrayList<>();

        for (Stock stock : stocks) {
            Optional<NewsSentiment> latest = newsSentimentRepository
                    .findTopByStockStockCodeOrderByCreatedAtDesc(stock.getStockCode());
            latest.ifPresent(news -> result.add(NewsTickerDto.from(news)));
        }

        return result;
    }
}