package com.example.invest_ai.domain.report.entity;

import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 투자 맞춤 리포트 테이블 (database.md §⑧ ai_investment_reports)
 *
 * RabbitMQ ReportWorker가 RAG 파이프라인을 거쳐 생성한 사용자별 맞춤 리포트 본문을 영구 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_investment_reports")
public class AiInvestmentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private Stock stock;

    @Column(name = "report_content", nullable = false, columnDefinition = "TEXT")
    private String reportContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AiInvestmentReport(User user, Stock stock, String reportContent) {
        this.user = user;
        this.stock = stock;
        this.reportContent = reportContent;
        this.createdAt = LocalDateTime.now();
    }
}