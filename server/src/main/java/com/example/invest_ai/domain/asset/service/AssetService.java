package com.example.invest_ai.domain.asset.service;

import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.entity.TradingHistory;
import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.asset.repository.TradingHistoryRepository;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.global.common.PageResponse;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.global.websocket.WebSocketSessionManager;
import com.example.invest_ai.infrastructure.kis.KisOrderClient;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 주문 실행 서비스 — WalletService + HoldingService + OrderSettlementService에 위임
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final WalletService walletService;
    private final HoldingService holdingService;
    private final StockRepository stockRepository;
    private final RedisPriceClient redisPriceClient;
    private final KisOrderClient kisOrderClient;
    private final OrderSettlementService orderSettlementService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final TradingHistoryRepository tradingHistoryRepository;

    /** GET /api/v1/assets/histories — 매매 체결 이력 페이징 조회 (api.md §3.3) */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getHistories(Long userId, String stockCode, String status, Pageable pageable) {
        Page<TradingHistory> page = tradingHistoryRepository.findHistories(userId, stockCode, status, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    /**
     * POST /api/v1/assets/orders — 수동 매매 주문
     *
     * clinerules.md §4.2: TradingHistory를 status='PENDING'으로 먼저 생성한 뒤 체결 API를 호출한다.
     * 성공 시에만 Wallet/Holding을 반영하고 markFilled(), 실패 시 잔고는 그대로 두고 markFailed(reason).
     *
     * 수동 주문은 조건에서 발동된 것이 아니므로 conditionId=NULL로 고정한다(database.md §⑥).
     * 클라이언트가 요청 바디에 conditionId를 실어 보내도 신뢰하지 않는다 — 조건 매칭 엔진에서만
     * 내부적으로 executeOrder(userId, request, conditionId)를 호출해 채워 넣는다.
     */
    public OrderResponse executeOrder(Long userId, OrderRequest request) {
        return executeOrder(userId, request, null);
    }

    /** ConditionMatchingEngine 등 내부 호출 전용 — 자동/반자동 체결 이력을 발동 조건에 연결한다. */
    public OrderResponse executeOrder(Long userId, OrderRequest request, Long conditionId) {
        String stockCode = request.stockCode();
        String orderType = request.orderType().toUpperCase();
        int quantity = request.quantity();
        String ordDvsn = request.ordDvsn();
        BigDecimal orderPrice = request.price();

        if (!"BUY".equals(orderType) && !"SELL".equals(orderType)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "orderType은 BUY 또는 SELL이어야 합니다.");
        }

        stockRepository.findById(stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND, "종목이 존재하지 않습니다: " + stockCode));

        // 체결가 확정: LIMIT 주문은 사용자가 지정한 지정가, MARKET 주문은 체결 시점의 실시간 시장가.
        // (예전 구현은 LIMIT 주문도 항상 currentPrice로 지갑/이력을 기록해 실제 주문가와 어긋났었다.)
        boolean isLimit = "00".equals(ordDvsn);
        if (isLimit && orderPrice == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "지정가(LIMIT) 주문은 price가 필요합니다.");
        }
        BigDecimal executionPrice = isLimit ? orderPrice : redisPriceClient.getCurrentPrice(stockCode);

        // 사전(비잠금) 검증 — 명백히 잔고/보유수량이 부족한 요청은 KIS 호출/PENDING 이력 생성 전에 거른다.
        // 락을 잡지 않으므로 동시 주문 경합 시 낙관적일 뿐이며, 최종 확정은 체결 성공 후
        // OrderSettlementService의 락 보유 구간에서 다시 검증한다.
        preCheckBalance(userId, orderType, stockCode, quantity, executionPrice);

        TradingHistory history = orderSettlementService.createPendingHistory(userId, conditionId, stockCode, orderType);

        // KIS REST 호출(레이트리미터 대기 포함 최대 수 초)은 DB 트랜잭션/락 밖에서 수행한다.
        try {
            kisOrderClient.executeOrder(stockCode, orderType, quantity, ordDvsn, orderPrice);
        } catch (Exception e) {
            log.error("KIS 주문 API 실패: {}", e.getMessage());
            TradingHistory failed = orderSettlementService.markFailed(history.getHistoryId(), e.getMessage());
            notifyOrderFailed(userId, failed);
            return toResponse(failed);
        }

        // KIS 체결 성공 시에만 지갑 행을 잠그고 잔고/보유량을 반영 (짧은 DB 전용 트랜잭션, REQUIRES_NEW)
        TradingHistory settled = orderSettlementService.settleFilledOrder(
                userId, history.getHistoryId(), orderType, stockCode, quantity, executionPrice);
        notifyOrderFilled(userId, settled);
        return toResponse(settled);
    }

    private void preCheckBalance(Long userId, String orderType, String stockCode, int quantity, BigDecimal executionPrice) {
        if ("BUY".equals(orderType)) {
            Wallet wallet = walletService.findByUserId(userId);
            BigDecimal totalCost = executionPrice.multiply(BigDecimal.valueOf(quantity));
            if (wallet.getBalance().compareTo(totalCost) < 0) {
                throw new CustomException(ErrorCode.INSUFFICIENT_BALANCE, "예수금이 부족합니다.");
            }
        } else {
            Holding holding = holdingService.findOrThrow(userId, stockCode);
            if (holding.getQuantity() < quantity) {
                throw new CustomException(ErrorCode.INSUFFICIENT_QUANTITY, "보유 수량이 부족합니다.");
            }
        }
    }

    /**
     * api.md §6.2 ORDER_FILLED — 자동/수동 주문 체결 완료 알림.
     * Map.of()는 값이 null이면 즉시 NPE를 던지므로(단순 실수로 필드 하나만 비어도 알림 전체가 죽음),
     * 페이로드 값 중 하나라도 비는 걸 완전히 배제할 수 없는 통지성 메시지에는 HashMap을 쓴다.
     */
    private void notifyOrderFilled(Long userId, TradingHistory history) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("historyId", history.getHistoryId());
        payload.put("stockCode", history.getStockCode());
        payload.put("executionPrice", history.getExecutionPrice());
        payload.put("executionQuantity", history.getExecutionQuantity());
        webSocketSessionManager.sendMessage(userId, "ORDER_FILLED", payload);
    }

    /** api.md §6.2 ORDER_FAILED — 자동/수동 주문 체결 실패 알림 (null-safety 사유는 notifyOrderFilled와 동일) */
    private void notifyOrderFailed(Long userId, TradingHistory history) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("historyId", history.getHistoryId());
        payload.put("stockCode", history.getStockCode());
        payload.put("failureReason", history.getFailureReason() != null ? history.getFailureReason() : "");
        webSocketSessionManager.sendMessage(userId, "ORDER_FAILED", payload);
    }

    private OrderResponse toResponse(TradingHistory history) {
        return new OrderResponse(
                history.getHistoryId(), history.getStockCode(), history.getOrderType(),
                history.getStatus(), history.getExecutionPrice(), history.getExecutionQuantity(),
                history.getTotalAmount(), history.getFailureReason(),
                history.getRequestedAt().toString(),
                history.getExecutedAt() != null ? history.getExecutedAt().toString() : null);
    }
}