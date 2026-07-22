package com.example.invest_ai.domain.asset.entity;

import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 유저 보유 종목 테이블 (database.md §④ user_holdings)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_holdings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_holdings_user_stock", columnNames = {"user_id", "stock_code"})
})
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holding_id")
    private Long holdingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "average_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal averagePrice;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Holding(Long userId, String stockCode, int quantity, BigDecimal averagePrice) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.quantity = quantity;
        this.averagePrice = averagePrice != null ? averagePrice : BigDecimal.ZERO;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 수량 증가 + 평단가 재계산
     * 공식: (기존평단가 * 기존수량 + price * qty) / (기존수량 + qty)
     */
    public void increaseQuantity(int qty, BigDecimal price) {
        BigDecimal totalCost = this.averagePrice.multiply(BigDecimal.valueOf(this.quantity))
                .add(price.multiply(BigDecimal.valueOf(qty)));
        this.quantity += qty;
        this.averagePrice = totalCost.divide(BigDecimal.valueOf(this.quantity), 4, RoundingMode.HALF_UP);
        this.updatedAt = LocalDateTime.now();
    }

    /** 수량 감소 — 보유 수량 부족 시 E4001 */
    public void decreaseQuantity(int qty) {
        if (this.quantity < qty) {
            throw new CustomException(ErrorCode.INSUFFICIENT_QUANTITY, "보유 수량이 부족합니다.");
        }
        this.quantity -= qty;
        this.updatedAt = LocalDateTime.now();
    }
}