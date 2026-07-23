package com.example.invest_ai.domain.news.repository;

import com.example.invest_ai.domain.news.entity.NewsSentiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * news_sentiments 테이블 Repository
 */
public interface NewsSentimentRepository extends JpaRepository<NewsSentiment, Long> {

    /** 뉴스 URL 기준 중복 수집 여부 확인 */
    boolean existsByNewsUrl(String newsUrl);

    /** 특정 종목의 최신 감성 분석 결과 1건 조회 */
    Optional<NewsSentiment> findTopByStockStockCodeOrderByCreatedAtDesc(String stockCode);

    /** 24시간 이내 뉴스 조회 — 코사인 유사도는 자바에서 직접 계산 */
    @Query("SELECT n FROM NewsSentiment n WHERE n.stock.stockCode = :stockCode AND n.publishedAt >= :since ORDER BY n.createdAt DESC")
    List<NewsSentiment> findRecentNewsByStockCode(
            @Param("stockCode") String stockCode,
            @Param("since") LocalDateTime since);
}