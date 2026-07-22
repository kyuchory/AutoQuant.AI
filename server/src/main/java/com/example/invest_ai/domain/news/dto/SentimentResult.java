package com.example.invest_ai.domain.news.dto;

/**
 * OpenAI 감성 분석 결과 DTO
 *
 * @param sentiment GOOD, BAD, NEUTRAL
 * @param aiScore   0(매우 부정) ~ 100(매우 긍정)
 * @param aiReason  점수를 매긴 이유 (한국어)
 */
public record SentimentResult(
        String sentiment,
        int aiScore,
        String aiReason
) {
    /**
     * 응답 파싱 실패 시 fallback용 NEUTRAL 결과
     */
    public static SentimentResult neutral(String reason) {
        return new SentimentResult("NEUTRAL", 50, reason);
    }
}