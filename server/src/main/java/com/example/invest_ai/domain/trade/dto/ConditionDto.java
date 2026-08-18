package com.example.invest_ai.domain.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자동 매매 조건 관련 DTO (api.md §4)
 *
 * TradingCondition(액션) + ConditionTrigger(트리거) 1:N 구조를 요청/응답에서도 유지한다.
 */
public class ConditionDto {

    /** 발동 조건(트리거) 요청 */
    public record TriggerRequest(
            @NotBlank String triggerType,     // PRICE, PROFIT_TARGET, STOP_LOSS, TRAILING_STOP, AI_SCORE
            @NotBlank String baseType,        // CURRENT_PRICE, AVG_PRICE, HIGHEST_PRICE, AI_SCORE
            @NotBlank String compareType,     // ABOVE, BELOW
            @NotNull BigDecimal targetValue,  // 목표값(절대가격 또는 %)
            Boolean isRate                    // true면 target_value는 %
    ) {}

    /** 조건 등록 요청 */
    public record TradingConditionRequest(
            @NotBlank String stockCode,
            @NotBlank String orderType,       // BUY, SELL
            @Min(1) int orderQuantity,
            String orderPriceType,            // MARKET(기본), LIMIT
            BigDecimal limitPrice,            // LIMIT 시 지정가
            String conditionLogic,            // AND(기본), OR
            String executionMode,             // AUTO(기본 - 완전자동), MANUAL(반자동 승인 제안)
            Boolean isPersistent,             // true면 실행 후에도 is_active 유지(반복 감시), 기본 false(1회성)
            @Valid @NotNull @Size(min = 1, max = 10) List<TriggerRequest> triggers
    ) {
        public String orderPriceType() {
            return orderPriceType != null ? orderPriceType : "MARKET";
        }
        public String conditionLogic() {
            return conditionLogic != null ? conditionLogic : "AND";
        }
        public String executionMode() {
            return executionMode != null ? executionMode : "AUTO";
        }
        public Boolean isPersistent() {
            return isPersistent != null ? isPersistent : false;
        }
    }

    /** 발동 조건(트리거) 응답 */
    public record TriggerResponse(
            Long triggerId,
            String triggerType,
            String baseType,
            String compareType,
            BigDecimal targetValue,
            Boolean isRate,
            BigDecimal trailingHighest
    ) {}

    /** 조건 상세 응답 */
    public record TradingConditionResponse(
            Long conditionId,
            String stockCode,
            String orderType,
            int orderQuantity,
            String orderPriceType,
            BigDecimal limitPrice,
            String conditionLogic,
            String executionMode,
            Boolean isActive,
            Boolean isPersistent,
            List<TriggerResponse> triggers,
            LocalDateTime createdAt
    ) {}

    /** 조건 감시 ON/OFF 토글 요청 */
    public record ActiveUpdateRequest(
            @NotNull Boolean isActive
    ) {}
}
