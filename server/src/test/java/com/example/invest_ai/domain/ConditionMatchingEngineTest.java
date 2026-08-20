package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.dto.AssetDto.OrderResponse;
import com.example.invest_ai.domain.asset.repository.HoldingRepository;
import com.example.invest_ai.domain.news.repository.NewsSentimentRepository;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.domain.trade.entity.ConditionTrigger;
import com.example.invest_ai.domain.trade.entity.TradingCondition;
import com.example.invest_ai.domain.trade.repository.TradingConditionRepository;
import com.example.invest_ai.domain.trade.service.ConditionMatchingEngine;
import com.example.invest_ai.domain.asset.service.AssetService;
import com.example.invest_ai.global.websocket.WebSocketSessionManager;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * docs/incident-trading-engine-blocking.md에 기록된 회귀 방지 테스트.
 *
 * - 이벤트 리스너가 웹소켓/RabbitMQ 수신 스레드를 블로킹하지 않도록 @Async가 반드시 붙어있는지
 * - 주문 동시성 락 TTL이 KIS 주문 최악 대기시간(8초)보다 짧아지지 않는지
 * - AND 결합에서 앞 트리거가 불일치해도 트레일링 스탑 고점 갱신이 누락되지 않는지
 */
@ExtendWith(MockitoExtension.class)
class ConditionMatchingEngineTest {

    private static final Long USER_ID = 1L;
    private static final String STOCK_CODE = "005930";

    @Mock private TradingConditionRepository conditionRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private NewsSentimentRepository newsSentimentRepository;
    @Mock private StockRepository stockRepository;
    @Mock private AssetService assetService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RedisPriceClient redisPriceClient;
    @Mock private WebSocketSessionManager webSocketSessionManager;
    @InjectMocks private ConditionMatchingEngine engine;

    private Stock stock() {
        return Stock.builder().stockCode(STOCK_CODE).stockName("삼성전자").build();
    }

    private TradingCondition autoBuyCondition(ConditionTrigger... triggers) {
        TradingCondition condition = TradingCondition.builder()
                .userId(USER_ID).stockCode(STOCK_CODE).orderType("BUY").orderQuantity(1)
                .orderPriceType("MARKET").conditionLogic("AND").executionMode("AUTO").isPersistent(false)
                .build();
        for (ConditionTrigger trigger : triggers) {
            condition.addTrigger(trigger);
        }
        // conditionId는 @GeneratedValue라 영속화 없이는 null인데, execute()의 PRICE_ALERT 알림 페이로드가
        // Map.of(...)로 만들어져 값 중 하나라도 null이면 NPE가 난다 (Map.of는 null을 허용하지 않음).
        // 실서비스에서는 DB에서 조회된 조건이라 PK가 항상 존재하므로 문제 없지만, 테스트에서는 직접 채워줘야 한다.
        setConditionId(condition, 99L);
        return condition;
    }

    private void setConditionId(TradingCondition condition, Long id) {
        try {
            java.lang.reflect.Field field = TradingCondition.class.getDeclaredField("conditionId");
            field.setAccessible(true);
            field.set(condition, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("onPriceUpdated/onNewsSentimentSaved: 이벤트 처리 스레드(KIS 웹소켓/RabbitMQ)를 블로킹하지 않도록 @Async가 붙어있어야 한다")
    void 이벤트리스너_Async어노테이션_필수() throws NoSuchMethodException {
        Method onPriceUpdated = ConditionMatchingEngine.class
                .getMethod("onPriceUpdated", com.example.invest_ai.domain.trade.event.PriceUpdatedEvent.class);
        Method onNewsSentimentSaved = ConditionMatchingEngine.class
                .getMethod("onNewsSentimentSaved", com.example.invest_ai.domain.trade.event.NewsSentimentSavedEvent.class);

        assertThat(onPriceUpdated.isAnnotationPresent(org.springframework.scheduling.annotation.Async.class))
                .as("onPriceUpdated는 KIS 웹소켓 수신 스레드에서 발행되므로 @Async 없이는 주문 응답(최대 8초)을 "
                        + "기다리며 그 스레드의 실시간 시세 처리가 전부 멈춘다")
                .isTrue();
        assertThat(onNewsSentimentSaved.isAnnotationPresent(org.springframework.scheduling.annotation.Async.class))
                .as("onNewsSentimentSaved는 RabbitMQ 뉴스 큐 리스너 스레드에서 발행되므로 동일한 이유로 @Async가 필요하다")
                .isTrue();
    }

    @Test
    @DisplayName("execute: 주문 동시성 락 TTL은 KIS 주문 최악 대기시간(레이트리미터 3초 + 타임아웃 5초=8초)보다 길어야 한다")
    void 동시성락_TTL은_8초보다_길다() {
        ConditionTrigger trigger = ConditionTrigger.builder()
                .triggerType("PRICE").baseType("CURRENT_PRICE").compareType("ABOVE")
                .targetValue(new BigDecimal("70000")).isRate(false).build();
        TradingCondition condition = autoBuyCondition(trigger);

        given(conditionRepository.findAllByStockCodeAndIsActiveTrue(STOCK_CODE)).willReturn(List.of(condition));
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("locked"), any(Duration.class))).willReturn(true);
        given(stockRepository.findById(STOCK_CODE)).willReturn(Optional.of(stock()));
        given(newsSentimentRepository.findTopByStockStockCodeOrderByCreatedAtDesc(STOCK_CODE))
                .willReturn(Optional.empty());
        given(assetService.executeOrder(eq(USER_ID), any(), any())).willReturn(new OrderResponse(
                1L, STOCK_CODE, "BUY", "FILLED", new BigDecimal("75000"), 1,
                new BigDecimal("75000"), null, "2026-08-20T09:00:00", "2026-08-20T09:00:01"));

        engine.evaluateConditionsForStock(STOCK_CODE, new BigDecimal("75000"));

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).setIfAbsent(anyString(), eq("locked"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue())
                .as("락이 KIS 응답보다 먼저 만료되면 같은 조건이 중복 체결될 수 있다 (2026-08-20 인시던트)")
                .isGreaterThanOrEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("evaluate: AND 결합에서 앞 트리거가 불일치해도 뒤에 놓인 트레일링 스탑의 고점은 갱신돼야 한다")
    void AND_단락평가에도_트레일링고점은_항상_갱신된다() {
        // 절대 매칭되지 않는 트리거를 앞에 둬서 evaluate()가 조기 반환하도록 유도
        ConditionTrigger neverMatches = ConditionTrigger.builder()
                .triggerType("PRICE").baseType("CURRENT_PRICE").compareType("ABOVE")
                .targetValue(new BigDecimal("999999999")).isRate(false).build();
        ConditionTrigger trailingStop = ConditionTrigger.builder()
                .triggerType("TRAILING_STOP").baseType("HIGHEST_PRICE").compareType("BELOW")
                .targetValue(new BigDecimal("-5")).isRate(true).build();
        TradingCondition condition = autoBuyCondition(neverMatches, trailingStop);

        given(conditionRepository.findAllByStockCodeAndIsActiveTrue(STOCK_CODE)).willReturn(List.of(condition));

        engine.evaluateConditionsForStock(STOCK_CODE, new BigDecimal("80000"));

        assertThat(trailingStop.getTrailingHighest())
                .as("neverMatches 트리거가 AND 루프를 조기 반환시켜도 트레일링 고점 갱신은 별도로 항상 수행돼야 한다")
                .isEqualByComparingTo("80000");
    }
}
