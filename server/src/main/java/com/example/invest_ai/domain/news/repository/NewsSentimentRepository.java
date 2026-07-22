package com.example.invest_ai.domain.news.repository;

import com.example.invest_ai.domain.news.entity.NewsSentiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * news_sentiments 테이블 Repository
 */
public interface NewsSentimentRepository extends JpaRepository<NewsSentiment, Long> {

    /**
     * 뉴스 URL 기준 중복 수집 여부 확인
     * uk_news_url 제약조건 대비 애플리케이션 레벨 사전 체크
     */
    boolean existsByNewsUrl(String newsUrl);

    /**
     * 특정 종목의 최신 감성 분석 결과 1건 조회
     * (대시보드 뉴스 티커, 자동매매 AI 점수 조건 평가용)
     */
    Optional<NewsSentiment> findTopByStockStockCodeOrderByCreatedAtDesc(String stockCode);
}