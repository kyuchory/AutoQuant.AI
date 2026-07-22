package com.example.invest_ai.domain.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 매매/체결 히스토리 테이블 (database.md §⑥ trading_histories)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "trading_histories")
public class TradingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "condition_id")
    private Long conditionId;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "order_type", nullable = false, length = 10)
    private String orderType;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "execution_price", precision = 18, scale = 4)
    private BigDecimal executionPrice;

    @Column(name = "execution_quantity")
    private Integer executionQuantity;

    @Column(name = "total_amount", precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Builder
    public TradingHistory(Long userId, Long conditionId, String stockCode, String orderType) {
        this.userId = userId;
        this.conditionId = conditionId;
        this.stockCode = stockCode;
        this.orderType = orderType;
        this.status = "PENDING";
        this.requestedAt = LocalDateTime.now();
    }

    /** 체결 완료 */
    public void markFilled(BigDecimal price, int qty) {
        this.status = "FILLED";
        this.executionPrice = price;
        this.executionQuantity = qty;
        this.totalAmount = price.multiply(BigDecimal.valueOf(qty));
        this.executedAt = LocalDateTime.now();
    }

    /** 체결 실패 */
    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failureReason = reason;
    }
}