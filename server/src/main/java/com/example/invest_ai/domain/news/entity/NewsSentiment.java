package com.example.invest_ai.domain.news.entity;

import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.infra.converter.FloatArrayToByteArrayConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 뉴스 감성 분석 및 RAG 임베딩 테이블 (database.md §⑦ news_sentiments)
 *
 * 수집된 네이버 뉴스 요약(description) 및 OpenAI 감성 분석 결과를 저장한다.
 * MySQL VECTOR(1536) 컬럼으로 임베딩 유사도 검색을 직접 수행한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "news_sentiments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_news_url", columnNames = "news_url")
})
public class NewsSentiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_id")
    private Long newsId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private Stock stock;

    @Column(name = "news_url", nullable = false, length = 500)
    private String newsUrl;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content_summary", nullable = false, columnDefinition = "TEXT")
    private String contentSummary;

    @Column(name = "sentiment", nullable = false, length = 10)
    private String sentiment;

    @Column(name = "ai_score", nullable = false)
    private Integer aiScore;

    @Column(name = "ai_reason", columnDefinition = "TEXT")
    private String aiReason;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "embedding", columnDefinition = "VECTOR(1536)")
    @Convert(converter = FloatArrayToByteArrayConverter.class)
    private float[] embedding;

    @Builder
    public NewsSentiment(Stock stock, String newsUrl, String title, String contentSummary,
                         String sentiment, Integer aiScore, String aiReason,
                         LocalDateTime publishedAt, float[] embedding) {
        this.stock = stock;
        this.newsUrl = newsUrl;
        this.title = title;
        this.contentSummary = contentSummary;
        this.sentiment = sentiment;
        this.aiScore = aiScore;
        this.aiReason = aiReason;
        this.publishedAt = publishedAt;
        this.createdAt = LocalDateTime.now();
        this.embedding = embedding;
    }
}
