package com.example.invest_ai.domain.report.dto;

import com.example.invest_ai.domain.report.entity.AiInvestmentReport;

import java.time.LocalDateTime;

/**
 * AI 투자 리포트 응답 DTO (clinerules §2.5 — record 사용)
 */
public record ReportDto(
        Long reportId,
        String stockCode,
        String stockName,
        String reportContent,
        LocalDateTime createdAt,
        boolean cacheHit
) {
    public static ReportDto fromEntity(AiInvestmentReport report, boolean cacheHit) {
        return new ReportDto(
                report.getReportId(),
                report.getStock().getStockCode(),
                report.getStock().getStockName(),
                report.getReportContent(),
                report.getCreatedAt(),
                cacheHit
        );
    }
}