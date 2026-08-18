package com.example.invest_ai.domain.trade.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 유저 자동 매매 발동 조건(트리거) 테이블 (database.md §⑤-1 condition_triggers)
 *
 * 하나의 TradingCondition(액션)에 복수 개의 트리거가 연결되고(N:1),
 * trading_conditions.condition_logic(AND/OR)에 따라 결합된다.
 *
 * base_type(측정 기준) + compare_type(비교 방향) + target_value(목표값/%) + is_rate(% 여부)
 * 조합으로 모든 유형의 조건(가격/손절/익절/트레일링/AI점수)을 범용 표현한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "condition_triggers")
public class ConditionTrigger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trigger_id")
    private Long triggerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condition_id", nullable = false, foreignKey = @ForeignKey(name = "fk_triggers_condition"))
    private TradingCondition condition;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType; // PRICE, PROFIT_TARGET, STOP_LOSS, TRAILING_STOP, AI_SCORE

    @Column(name = "base_type", nullable = false, length = 20)
    private String baseType; // CURRENT_PRICE, AVG_PRICE, HIGHEST_PRICE, AI_SCORE

    @Column(name = "compare_type", nullable = false, length = 10)
    private String compareType; // ABOVE, BELOW

    @Column(name = "target_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal targetValue; // 목표값(절대가격 또는 %)

    @Column(name = "is_rate", nullable = false)
    private Boolean isRate; // true면 target_value는 %

    @Column(name = "trailing_highest", precision = 18, scale = 4)
    private BigDecimal trailingHighest; // 트레일링 스탑 추적 고점 (TRAILING_STOP 전용)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ConditionTrigger(String triggerType, String baseType, String compareType,
                            BigDecimal targetValue, Boolean isRate, BigDecimal trailingHighest) {
        this.triggerType = triggerType;
        this.baseType = baseType;
        this.compareType = compareType;
        this.targetValue = targetValue;
        this.isRate = isRate != null ? isRate : false;
        this.trailingHighest = trailingHighest;
        this.createdAt = LocalDateTime.now();
    }

    /** 트레일링 스탑 고점 갱신 */
    public void updateTrailingHighest(BigDecimal newHighest) {
        if (newHighest == null) return;
        if (this.trailingHighest == null || newHighest.compareTo(this.trailingHighest) > 0) {
            this.trailingHighest = newHighest;
        }
    }

    /** 연관관계 바인딩 (TradingCondition.addTrigger에서 호출) */
    void bindCondition(TradingCondition condition) {
        this.condition = condition;
    }
}