package com.example.invest_ai.domain.trade.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 유저 자동 매매 조건(액션/주문) 테이블 (database.md §⑤ trading_conditions)
 *
 * 조건 주문의 "액션(주문)" 부분만 담는 부모 테이블.
 * "언제 발동할지"는 §⑤-1 ConditionTrigger(condition_triggers)로 분리한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "trading_conditions")
public class TradingCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "condition_id")
    private Long conditionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "order_type", nullable = false, length = 10)
    private String orderType; // BUY, SELL

    @Column(name = "order_quantity", nullable = false)
    private int orderQuantity;

    @Column(name = "order_price_type", nullable = false, length = 10)
    private String orderPriceType; // MARKET, LIMIT

    @Column(name = "limit_price", precision = 18, scale = 4)
    private BigDecimal limitPrice; // LIMIT 주문 시 지정가

    @Column(name = "condition_logic", nullable = false, length = 3)
    private String conditionLogic; // AND, OR

    @Column(name = "execution_mode", nullable = false, length = 10)
    private String executionMode; // AUTO, MANUAL

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_persistent", nullable = false)
    private Boolean isPersistent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "condition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConditionTrigger> triggers = new ArrayList<>();

    @Builder
    public TradingCondition(Long userId, String stockCode, String orderType, int orderQuantity,
                            String orderPriceType, BigDecimal limitPrice, String conditionLogic,
                            String executionMode, Boolean isPersistent) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.orderType = orderType;
        this.orderQuantity = orderQuantity;
        this.orderPriceType = orderPriceType != null ? orderPriceType : "MARKET";
        this.limitPrice = limitPrice;
        this.conditionLogic = conditionLogic != null ? conditionLogic : "AND";
        this.executionMode = executionMode != null ? executionMode : "AUTO";
        this.isPersistent = isPersistent != null ? isPersistent : false;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 조건 실행 후 감시 상태 정리.
     * isPersistent=false(1회성) 조건만 비활성화하고, isPersistent=true(반복 감시) 조건은 is_active를 유지한다.
     */
    public void deactivateIfNotPersistent() {
        if (Boolean.TRUE.equals(this.isPersistent)) {
            return;
        }
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /** 조건 감시 활성/비활성 토글 */
    public void updateActive(boolean active) {
        this.isActive = active;
        this.updatedAt = LocalDateTime.now();
    }

    /** 액션(주문) 필드 수정 */
    public void updateAction(String orderType, int orderQuantity,
                             String orderPriceType, BigDecimal limitPrice, String conditionLogic,
                             String executionMode, Boolean isPersistent) {
        this.orderType = orderType;
        this.orderQuantity = orderQuantity;
        this.orderPriceType = orderPriceType != null ? orderPriceType : "MARKET";
        this.limitPrice = limitPrice;
        this.conditionLogic = conditionLogic != null ? conditionLogic : "AND";
        this.executionMode = executionMode != null ? executionMode : "AUTO";
        this.isPersistent = isPersistent != null ? isPersistent : false;
        this.updatedAt = LocalDateTime.now();
    }

    /** 트리거 전체 초기화 (orphanRemoval로 기존 트리거 삭제) */
    public void clearTriggers() {
        this.triggers.clear();
        this.updatedAt = LocalDateTime.now();
    }

    /** 트리거 추가 (양방향 연관관계 유지) */
    public void addTrigger(ConditionTrigger trigger) {
        this.triggers.add(trigger);
        trigger.bindCondition(this);
    }
}
