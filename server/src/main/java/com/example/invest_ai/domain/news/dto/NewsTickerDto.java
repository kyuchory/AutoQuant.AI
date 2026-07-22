package com.example.invest_ai.domain.news.dto;

import java.time.LocalDateTime;

/**
 * 대시보드 뉴스 티커 DTO — 10종목 최신 뉴스 응답
 */
public record NewsTickerDto(
        String stockCode,
        String stockName,
        String title,
        String newsUrl,
        String sentiment,
        int aiScore,
        LocalDateTime publishedAt
) {
    public static NewsTickerDto from(com.example.invest_ai.domain.news.entity.NewsSentiment news) {
        return new NewsTickerDto(
                news.getStock().getStockCode(),
                news.getStock().getStockName(),
                news.getTitle(),
                news.getNewsUrl(),
                news.getSentiment(),
                news.getAiScore(),
                news.getPublishedAt()
        );
    }
}
