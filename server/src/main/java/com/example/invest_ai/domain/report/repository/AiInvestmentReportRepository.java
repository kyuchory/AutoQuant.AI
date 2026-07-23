package com.example.invest_ai.domain.report.repository;

import com.example.invest_ai.domain.report.entity.AiInvestmentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ai_investment_reports 테이블 Repository
 */
public interface AiInvestmentReportRepository extends JpaRepository<AiInvestmentReport, Long> {

    /**
     * 특정 종목의 최신 리포트 1건 조회 (Redis 캐시 미스 시 fallback)
     */
    Optional<AiInvestmentReport> findTopByStockStockCodeOrderByCreatedAtDesc(String stockCode);
}