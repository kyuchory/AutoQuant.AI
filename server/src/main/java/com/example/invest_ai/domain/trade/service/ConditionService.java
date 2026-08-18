package com.example.invest_ai.domain.trade.service;

import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.domain.trade.dto.ConditionDto.*;
import com.example.invest_ai.domain.trade.entity.ConditionTrigger;
import com.example.invest_ai.domain.trade.entity.TradingCondition;
import com.example.invest_ai.domain.trade.repository.TradingConditionRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 자동 매매 조건 CRUD 서비스 (api.md §4)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConditionService {

    private static final String TRAILING_STOP = "TRAILING_STOP";

    private static final Set<String> VALID_ORDER_TYPES = Set.of("BUY", "SELL");
    private static final Set<String> VALID_ORDER_PRICE_TYPES = Set.of("MARKET", "LIMIT");
    private static final Set<String> VALID_CONDITION_LOGICS = Set.of("AND", "OR");
    private static final Set<String> VALID_EXECUTION_MODES = Set.of("AUTO", "MANUAL");
    private static final Set<String> VALID_TRIGGER_TYPES =
            Set.of("PRICE", "PROFIT_TARGET", "STOP_LOSS", "TRAILING_STOP", "AI_SCORE");
    private static final Set<String> VALID_BASE_TYPES =
            Set.of("CURRENT_PRICE", "AVG_PRICE", "HIGHEST_PRICE", "AI_SCORE");
    private static final Set<String> VALID_COMPARE_TYPES = Set.of("ABOVE", "BELOW");

    private final TradingConditionRepository conditionRepository;
    private final StockRepository stockRepository;
    private final RedisPriceClient redisPriceClient;

    /** 조건 등록 */
    @Transactional
    public TradingConditionResponse createCondition(Long userId, TradingConditionRequest request) {
        String orderType = upper(request.orderType());
        String orderPriceType = upper(request.orderPriceType());
        String conditionLogic = upper(request.conditionLogic());
        String executionMode = upper(request.executionMode());
        validateOrderType(orderType);
        validateOrderPriceType(orderPriceType, request.limitPrice());
        validateConditionLogic(conditionLogic);
        validateExecutionMode(executionMode);
        validateTriggers(request.triggers());

        Stock stock = stockRepository.findById(request.stockCode())
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND, "종목이 존재하지 않습니다: " + request.stockCode()));

        TradingCondition condition = TradingCondition.builder()
                .userId(userId)
                .stockCode(stock.getStockCode())
                .orderType(orderType)
                .orderQuantity(request.orderQuantity())
                .orderPriceType(orderPriceType)
                .limitPrice(request.limitPrice())
                .conditionLogic(conditionLogic)
                .executionMode(executionMode)
                .isPersistent(request.isPersistent())
                .build();

        for (TriggerRequest tr : request.triggers()) {
            String triggerType = upper(tr.triggerType());
            BigDecimal trailingHighest = null;
            // 트레일링 스탑은 등록 시점의 현재가로 고점 초기화
            if (TRAILING_STOP.equals(triggerType)) {
                trailingHighest = redisPriceClient.getCurrentPrice(request.stockCode());
            }
            ConditionTrigger trigger = ConditionTrigger.builder()
                    .triggerType(triggerType)
                    .baseType(upper(tr.baseType()))
                    .compareType(upper(tr.compareType()))
                    .targetValue(tr.targetValue())
                    .isRate(tr.isRate())
                    .trailingHighest(trailingHighest)
                    .build();
            condition.addTrigger(trigger);
        }

        TradingCondition saved = conditionRepository.save(condition);
        return toResponse(saved);
    }

    /** 조건 목록 조회 */
    @Transactional(readOnly = true)
    public List<TradingConditionResponse> getConditions(Long userId) {
        return conditionRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 조건 삭제 */
    @Transactional
    public void deleteCondition(Long userId, Long conditionId) {
        TradingCondition condition = findOwnedCondition(userId, conditionId);
        conditionRepository.delete(condition);
    }

    /** 조건 감시 ON/OFF 토글 */
    @Transactional
    public TradingConditionResponse updateActive(Long userId, Long conditionId, boolean active) {
        TradingCondition condition = findOwnedCondition(userId, conditionId);
        condition.updateActive(active);
        return toResponse(condition);
    }

    /** 조건 수정 (액션 + 트리거 전체 교체) */
    @Transactional
    public TradingConditionResponse updateCondition(Long userId, Long conditionId, TradingConditionRequest request) {
        String orderType = upper(request.orderType());
        String orderPriceType = upper(request.orderPriceType());
        String conditionLogic = upper(request.conditionLogic());
        String executionMode = upper(request.executionMode());
        validateOrderType(orderType);
        validateOrderPriceType(orderPriceType, request.limitPrice());
        validateConditionLogic(conditionLogic);
        validateExecutionMode(executionMode);
        validateTriggers(request.triggers());

        TradingCondition condition = findOwnedCondition(userId, conditionId);
        condition.updateAction(
                orderType,
                request.orderQuantity(),
                orderPriceType,
                request.limitPrice(),
                conditionLogic,
                executionMode,
                request.isPersistent());

        // 트리거 전체 교체 (orphanRemoval로 기존 삭제)
        condition.clearTriggers();
        for (TriggerRequest tr : request.triggers()) {
            String triggerType = upper(tr.triggerType());
            BigDecimal trailingHighest = null;
            if (TRAILING_STOP.equals(triggerType)) {
                trailingHighest = redisPriceClient.getCurrentPrice(condition.getStockCode());
            }
            ConditionTrigger trigger = ConditionTrigger.builder()
                    .triggerType(triggerType)
                    .baseType(upper(tr.baseType()))
                    .compareType(upper(tr.compareType()))
                    .targetValue(tr.targetValue())
                    .isRate(tr.isRate())
                    .trailingHighest(trailingHighest)
                    .build();
            condition.addTrigger(trigger);
        }

        return toResponse(condition);
    }

    /** 소유권 검증 후 조건 조회 */
    private TradingCondition findOwnedCondition(Long userId, Long conditionId) {
        return conditionRepository.findByConditionIdAndUserId(conditionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONDITION_NOT_FOUND, "매매 조건을 찾을 수 없습니다."));
    }

    private static String upper(String value) {
        return value != null ? value.toUpperCase() : null;
    }

    /** order_type 값 검증 (chk_conditions_order_type) */
    private void validateOrderType(String orderType) {
        if (!VALID_ORDER_TYPES.contains(orderType)) {
            throw new CustomException(ErrorCode.INVALID_CONDITION, "주문 구분은 BUY 또는 SELL이어야 합니다.");
        }
    }

    /** order_price_type / limit_price 값-타입 검증 (chk_conditions_order_price_type, chk_conditions_limit_price) */
    private void validateOrderPriceType(String orderPriceType, BigDecimal limitPrice) {
        if (!VALID_ORDER_PRICE_TYPES.contains(orderPriceType)) {
            throw new CustomException(ErrorCode.INVALID_CONDITION, "주문 방식은 MARKET 또는 LIMIT이어야 합니다.");
        }
        if ("LIMIT".equals(orderPriceType) && (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new CustomException(ErrorCode.INVALID_CONDITION, "지정가(LIMIT) 주문은 limitPrice를 입력해야 합니다.");
        }
        if ("MARKET".equals(orderPriceType) && limitPrice != null) {
            throw new CustomException(ErrorCode.INVALID_CONDITION, "시장가(MARKET) 주문은 limitPrice를 입력할 수 없습니다.");
        }
    }

    /** condition_logic 값 검증 (chk_conditions_logic) */
    private void validateConditionLogic(String conditionLogic) {
        if (!VALID_CONDITION_LOGICS.contains(conditionLogic)) {
            throw new CustomException(ErrorCode.INVALID_CONDITION, "조건 결합 방식은 AND 또는 OR이어야 합니다.");
        }
    }

    /** execution_mode 값 검증 (chk_conditions_execution_mode) */
    private void validateExecutionMode(String executionMode) {
        if (!VALID_EXECUTION_MODES.contains(executionMode)) {
            throw new CustomException(ErrorCode.INVALID_CONDITION, "실행 모드는 AUTO 또는 MANUAL이어야 합니다.");
        }
    }

    /** 트리거 값-타입 검증 (chk_triggers_trigger_type / base_type / compare_type / trailing) */
    private void validateTriggers(List<TriggerRequest> triggers) {
        for (TriggerRequest tr : triggers) {
            String triggerType = upper(tr.triggerType());
            String baseType = upper(tr.baseType());
            String compareType = upper(tr.compareType());

            if (!VALID_TRIGGER_TYPES.contains(triggerType)) {
                throw new CustomException(ErrorCode.INVALID_TRIGGER, "유효하지 않은 트리거 유형입니다: " + tr.triggerType());
            }
            if (!VALID_BASE_TYPES.contains(baseType)) {
                throw new CustomException(ErrorCode.INVALID_TRIGGER, "유효하지 않은 기준 유형입니다: " + tr.baseType());
            }
            if (!VALID_COMPARE_TYPES.contains(compareType)) {
                throw new CustomException(ErrorCode.INVALID_TRIGGER, "유효하지 않은 비교 유형입니다: " + tr.compareType());
            }
            // 트레일링 스탑은 base_type=HIGHEST_PRICE, compare_type=BELOW, is_rate=true 고정
            if (TRAILING_STOP.equals(triggerType)) {
                boolean valid = "HIGHEST_PRICE".equals(baseType) && "BELOW".equals(compareType) && Boolean.TRUE.equals(tr.isRate());
                if (!valid) {
                    throw new CustomException(ErrorCode.INVALID_TRIGGER, "트레일링 스탑은 baseType=HIGHEST_PRICE, compareType=BELOW, isRate=true 여야 합니다.");
                }
            }
        }
    }

    private TradingConditionResponse toResponse(TradingCondition c) {
        List<TriggerResponse> triggerResponses = c.getTriggers().stream()
                .map(t -> new TriggerResponse(
                        t.getTriggerId(), t.getTriggerType(), t.getBaseType(),
                        t.getCompareType(), t.getTargetValue(), t.getIsRate(), t.getTrailingHighest()))
                .toList();

        return new TradingConditionResponse(
                c.getConditionId(), c.getStockCode(), c.getOrderType(), c.getOrderQuantity(),
                c.getOrderPriceType(), c.getLimitPrice(), c.getConditionLogic(), c.getExecutionMode(),
                c.getIsActive(), c.getIsPersistent(), triggerResponses, c.getCreatedAt());
    }
}
