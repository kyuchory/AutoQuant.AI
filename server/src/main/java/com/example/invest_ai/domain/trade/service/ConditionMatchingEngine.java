package com.example.invest_ai.domain.trade.service;

import com.example.invest_ai.domain.asset.dto.AssetDto.OrderRequest;
import com.example.invest_ai.domain.asset.dto.AssetDto.OrderResponse;
import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.repository.HoldingRepository;
import com.example.invest_ai.domain.asset.service.AssetService;
import com.example.invest_ai.domain.news.entity.NewsSentiment;
import com.example.invest_ai.domain.news.repository.NewsSentimentRepository;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.domain.trade.entity.ConditionTrigger;
import com.example.invest_ai.domain.trade.entity.TradingCondition;
import com.example.invest_ai.domain.trade.event.NewsSentimentSavedEvent;
import com.example.invest_ai.domain.trade.event.PriceUpdatedEvent;
import com.example.invest_ai.domain.trade.repository.TradingConditionRepository;
import com.example.invest_ai.global.websocket.WebSocketSessionManager;
import com.example.invest_ai.infra.config.RedisKeys;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 조건 매칭 엔진 (workflow.md §7)
 *
 * 1. KIS WebSocket 시세 수신 → PriceUpdatedEvent 발행 → 조건 평가
 * 2. 뉴스 AI 분석 완료 → NewsSentimentSavedEvent 발행 → 조건 즉시 평가 (AI 점수 기반 실시간 매매)
 *
 * base_type별 기준값 조회 → is_rate 환산 → compare_type 비교 → condition_logic(AND/OR) 결합
 * →
 * 충족 시 WebSocket 알림 전송 + 주문 실행 + rate:order:lock 2차 방어 + 1회성 조건 비활성화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionMatchingEngine {

    private static final String TRAILING_STOP = "TRAILING_STOP";
    /** clinerules.md §4.2 재시도 정책 — 이 횟수를 넘겨 계속 실패하면 조건을 비활성화한다 */
    private static final int MAX_ORDER_RETRY = 5;

    private final TradingConditionRepository conditionRepository;
    private final HoldingRepository holdingRepository;
    private final NewsSentimentRepository newsSentimentRepository;
    private final StockRepository stockRepository;
    private final AssetService assetService;
    private final StringRedisTemplate redisTemplate;
    private final RedisPriceClient redisPriceClient;
    private final WebSocketSessionManager webSocketSessionManager;

    /** 1. 시세 갱신 이벤트 구독 (KIS WebSocket) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPriceUpdated(PriceUpdatedEvent event) {
        evaluateConditionsForStock(event.stockCode(), event.currentPrice());
    }

    /** 2. AI 뉴스 감성 분석 저장 이벤트 구독 (실시간 AI 점수 조건 매칭) */
    @EventListener
    public void onNewsSentimentSaved(NewsSentimentSavedEvent event) {
        log.info("[매매 엔진] 신규 뉴스 AI 점수 수신: stock={}, score={}, sentiment={}",
                event.stockCode(), event.aiScore(), event.sentiment());
        try {
            BigDecimal currentPrice = redisPriceClient.getCurrentPrice(event.stockCode());
            evaluateConditionsForStock(event.stockCode(), currentPrice);
        } catch (Exception e) {
            log.warn("[매매 엔진] 시세 조회 불가로 뉴스 트리거 매칭 스킵: stock={}, error={}",
                    event.stockCode(), e.getMessage());
        }
    }

    /** 특정 종목의 활성 조건 평가 및 실행 */
    @Transactional
    public void evaluateConditionsForStock(String stockCode, BigDecimal currentPrice) {
        List<TradingCondition> conditions = conditionRepository.findAllByStockCodeAndIsActiveTrue(stockCode);
        if (conditions.isEmpty())
            return;

        for (TradingCondition condition : conditions) {
            try {
                if (evaluate(condition, currentPrice)) {
                    execute(condition, currentPrice);
                }
            } catch (Exception e) {
                // 조건 평가/실행 예외는 로그만 남기고 계속 (다른 조건에 영향 X)
                log.warn("조건 매칭 실패 (conditionId={}): {}", condition.getConditionId(), e.getMessage());
            }
        }
    }

    /** 조건 평가 — 트리거들을 condition_logic(AND/OR)으로 결합 */
    private boolean evaluate(TradingCondition condition, BigDecimal currentPrice) {
        List<ConditionTrigger> triggers = condition.getTriggers();
        if (triggers.isEmpty())
            return false;

        boolean isAnd = !"OR".equals(condition.getConditionLogic());

        boolean result = isAnd;
        for (ConditionTrigger trigger : triggers) {
            boolean matched = evaluateTrigger(condition, trigger, currentPrice);
            if (isAnd) {
                result = result && matched;
                if (!result)
                    return false;
            } else {
                result = result || matched;
                if (result)
                    return true;
            }
        }
        return result;
    }

    /** 단일 트리거 평가 */
    private boolean evaluateTrigger(TradingCondition condition, ConditionTrigger trigger, BigDecimal currentPrice) {
        // 트레일링 스탑: 고점 갱신 먼저 수행
        if (TRAILING_STOP.equals(trigger.getTriggerType())) {
            if (trigger.getTrailingHighest() == null || currentPrice.compareTo(trigger.getTrailingHighest()) > 0) {
                trigger.updateTrailingHighest(currentPrice);
            }
        }

        BigDecimal compareValue = resolveCompareValue(trigger.getBaseType(), condition, currentPrice);
        if (compareValue == null)
            return false;

        BigDecimal threshold = resolveThreshold(trigger, condition, currentPrice);
        if (threshold == null)
            return false;

        if ("ABOVE".equals(trigger.getCompareType())) {
            return compareValue.compareTo(threshold) >= 0;
        } else { // BELOW
            return compareValue.compareTo(threshold) <= 0;
        }
    }

    /** 비교 대상 값(현재가 또는 AI 점수) 조회 */
    private BigDecimal resolveCompareValue(String baseType, TradingCondition condition, BigDecimal currentPrice) {
        return switch (baseType) {
            case "AI_SCORE" -> {
                NewsSentiment latest = newsSentimentRepository
                        .findTopByStockStockCodeOrderByCreatedAtDesc(condition.getStockCode())
                        .orElse(null);
                yield latest != null ? BigDecimal.valueOf(latest.getAiScore()) : null;
            }
            case "CURRENT_PRICE", "AVG_PRICE", "HIGHEST_PRICE" -> currentPrice;
            default -> null;
        };
    }

    /** 임계값(threshold) 산정 */
    private BigDecimal resolveThreshold(ConditionTrigger trigger, TradingCondition condition, BigDecimal currentPrice) {
        if (!Boolean.TRUE.equals(trigger.getIsRate())) {
            return trigger.getTargetValue();
        }

        // is_rate=true → 기준값 대비 % 오프셋
        BigDecimal baseValue = switch (trigger.getBaseType()) {
            case "AVG_PRICE" -> getAveragePrice(condition.getUserId(), condition.getStockCode());
            case "HIGHEST_PRICE" -> trigger.getTrailingHighest();
            case "CURRENT_PRICE" -> currentPrice;
            default -> null;
        };
        if (baseValue == null)
            return null;

        // baseValue × (1 + targetValue/100)
        BigDecimal ratio = BigDecimal.ONE.add(
                trigger.getTargetValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return baseValue.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
    }

    /** 평단가 조회 (보유 없으면 null) */
    private BigDecimal getAveragePrice(Long userId, String stockCode) {
        Holding holding = holdingRepository.findByUserIdAndStockCode(userId, stockCode).orElse(null);
        return holding != null ? holding.getAveragePrice() : null;
    }

    /**
     * 조건 충족 시 실행 모드(executionMode)에 따라 분기:
     * - AUTO  : 즉시 주문 체결 (기존 동작)
     * - MANUAL: WebSocket ORDER_PROPOSAL 이벤트로 AI 근거 + 뉴스(발행시각) 포함 반자동 제안 모달 전송
     */
    private void execute(TradingCondition condition, BigDecimal currentPrice) {
        // ── 재시도 백오프 (직전 체결 실패 후 10초 이내면 이번 틱은 건너뛴다) ──
        String backoffKey = RedisKeys.rateConditionBackoff(condition.getConditionId());
        if (Boolean.TRUE.equals(redisTemplate.hasKey(backoffKey))) {
            return;
        }

        // ── 동시성 락 (Redis 2차 방어선) ──
        String lockKey = RedisKeys.rateOrderLock(condition.getUserId(), condition.getStockCode());
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(4));
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("동시성 락 획득 실패로 주문/제안 스킵: conditionId={}", condition.getConditionId());
            return;
        }

        // 공통: 종목명 + 최신 뉴스 조회
        String stockName = stockRepository.findById(condition.getStockCode())
                .map(Stock::getStockName)
                .orElse(condition.getStockCode());

        NewsSentiment latestNews = newsSentimentRepository
                .findTopByStockStockCodeOrderByCreatedAtDesc(condition.getStockCode()).orElse(null);

        // ──────────────────────────────────────────────────────────────
        // MANUAL 모드: ORDER_PROPOSAL WebSocket 이벤트 전송 → 모달 팝업
        // ──────────────────────────────────────────────────────────────
        if ("MANUAL".equalsIgnoreCase(condition.getExecutionMode())) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("conditionId", condition.getConditionId());
            payload.put("stockCode",   condition.getStockCode());
            payload.put("stockName",   stockName);
            payload.put("orderType",   condition.getOrderType());
            payload.put("orderQuantity",  condition.getOrderQuantity());
            payload.put("orderPriceType", condition.getOrderPriceType());
            payload.put("limitPrice",  condition.getLimitPrice());
            payload.put("currentPrice", currentPrice);

            // 트리거 발동 사유 문구
            boolean hasAiTrigger = condition.getTriggers().stream()
                    .anyMatch(t -> "AI_SCORE".equals(t.getTriggerType()));
            String triggerReason = hasAiTrigger
                    ? "AI 호재 점수 " + (latestNews != null ? latestNews.getAiScore() : 0) + "점 감지"
                    : "설정 조건 도달 (현재가: ₩" + currentPrice.toPlainString() + ")";
            payload.put("triggerReason", triggerReason);

            // AI 근거 + 뉴스 발행 시각 탑재
            if (latestNews != null) {
                payload.put("aiScore",        latestNews.getAiScore());
                payload.put("aiSentiment",    latestNews.getSentiment());
                payload.put("aiReason",       latestNews.getAiReason());
                payload.put("newsTitle",      latestNews.getTitle());
                payload.put("newsUrl",        latestNews.getNewsUrl());
                payload.put("newsSummary",    latestNews.getContentSummary());
                payload.put("newsPublishedAt",
                        latestNews.getPublishedAt().format(DateTimeFormatter.ISO_DATE_TIME));
            }

            log.info("📢 [반자동 제안] ORDER_PROPOSAL 전송: userId={}, stock={}, reason={}",
                    condition.getUserId(), condition.getStockCode(), triggerReason);

            webSocketSessionManager.sendMessage(condition.getUserId(), "ORDER_PROPOSAL", payload);

            // 1회성 조건만 비활성화 (isPersistent=true면 계속 감시), 알림 중복 방지
            condition.deactivateIfNotPersistent();
            conditionRepository.save(condition);
            return;
        }

        // ──────────────────────────────────────────────────────────────
        // AUTO 모드: 즉시 주문 체결 + 보조 WebSocket 알림
        // ──────────────────────────────────────────────────────────────
        boolean hasAiTrigger = condition.getTriggers().stream()
                .anyMatch(t -> "AI_SCORE".equals(t.getTriggerType()));
        if (hasAiTrigger) {
            int score = latestNews != null ? latestNews.getAiScore() : 0;
            webSocketSessionManager.sendMessage(condition.getUserId(), "AI_SCORE_ALERT", Map.of(
                    "conditionId", condition.getConditionId(),
                    "stockCode",   condition.getStockCode(),
                    "aiScore",     score));
        } else {
            webSocketSessionManager.sendMessage(condition.getUserId(), "PRICE_ALERT", Map.of(
                    "conditionId",  condition.getConditionId(),
                    "stockCode",    condition.getStockCode(),
                    "currentPrice", currentPrice));
        }

        String ordDvsn = "LIMIT".equals(condition.getOrderPriceType()) ? "00" : "01";
        OrderRequest orderRequest = new OrderRequest(
                condition.getStockCode(),
                condition.getOrderType(),
                condition.getOrderQuantity(),
                ordDvsn,
                condition.getLimitPrice());

        log.info("✅ [완전자동] 조건 충족 → 주문 즉시 체결: conditionId={}, stock={}, orderType={}, qty={}",
                condition.getConditionId(), condition.getStockCode(),
                condition.getOrderType(), condition.getOrderQuantity());

        OrderResponse result = assetService.executeOrder(condition.getUserId(), orderRequest, condition.getConditionId());

        if ("FILLED".equals(result.status())) {
            // 체결 성공 → 재시도 카운터 정리 + 1회성 조건만 비활성화 (isPersistent=true면 계속 감시)
            redisTemplate.delete(RedisKeys.rateConditionRetryCount(condition.getConditionId()));
            condition.deactivateIfNotPersistent();
            conditionRepository.save(condition);
            return;
        }

        // 체결 실패 → redisflow.md §2.6 / clinerules.md §4.2: is_active는 체결 "성공" 시에만 끈다.
        // 대신 재시도 폭주를 막기 위해 짧은 백오프를 걸고, 반복 실패가 누적되면 그때 비활성화한다.
        String retryCountKey = RedisKeys.rateConditionRetryCount(condition.getConditionId());
        Long failCount = redisTemplate.opsForValue().increment(retryCountKey);
        redisTemplate.expire(retryCountKey, Duration.ofMinutes(5));

        if (failCount != null && failCount >= MAX_ORDER_RETRY) {
            log.error("🚫 [완전자동] 재시도 {}회 초과로 조건 비활성화: conditionId={}, reason={}",
                    MAX_ORDER_RETRY, condition.getConditionId(), result.failureReason());
            condition.updateActive(false);
            conditionRepository.save(condition);
            redisTemplate.delete(retryCountKey);
            return;
        }

        log.warn("⚠️ [완전자동] 주문 체결 실패({}회째) → 10초 백오프 후 재시도: conditionId={}, reason={}",
                failCount, condition.getConditionId(), result.failureReason());
        redisTemplate.opsForValue().set(RedisKeys.rateConditionBackoff(condition.getConditionId()), "1", Duration.ofSeconds(10));
    }
}
