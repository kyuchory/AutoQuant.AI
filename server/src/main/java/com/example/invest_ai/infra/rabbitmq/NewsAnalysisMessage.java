package com.example.invest_ai.infra.rabbitmq;

import com.example.invest_ai.domain.report.dto.NaverNewsResponse;

import java.io.Serializable;

/**
 * RabbitMQ news-queue 메시지 래퍼
 *
 * @param stockCode 뉴스가 속한 종목 코드
 * @param item      네이버 뉴스 검색 결과 아이템
 */
public record NewsAnalysisMessage(
        String stockCode,
        NaverNewsResponse.Item item
) implements Serializable {
}