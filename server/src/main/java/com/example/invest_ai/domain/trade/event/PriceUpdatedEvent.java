package com.example.invest_ai.domain.trade.event;

import java.math.BigDecimal;

/**
 * KIS WebSocket에서 실시간 체결가를 수신해 Redis를 갱신할 때 발행하는 이벤트.
 * 조건 매칭 엔진(ConditionMatchingEngine)이 이 이벤트를 구독해 조건 평가를 트리거한다.
 *
 * 인프라(KisWebsocketClient)가 도메인을 직접 의존하지 않고,
 * Spring 이벤트로 느슨하게 결합하기 위한 매개체.
 */
public record PriceUpdatedEvent(String stockCode, BigDecimal currentPrice) {
}