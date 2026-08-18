package com.example.invest_ai.domain.trade.event;

/**
 * 네이버 뉴스 감성 분석 결과(aiScore)가 DB에 저장된 직후 발행되는 이벤트.
 * 조건 매칭 엔진(ConditionMatchingEngine)이 이 이벤트를 구독하여 AI 점수 기반 자동 매매 조건을 즉각 평가한다.
 */
public record NewsSentimentSavedEvent(String stockCode, int aiScore, String sentiment) {
}
