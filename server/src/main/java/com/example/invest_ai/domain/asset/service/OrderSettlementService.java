package com.example.invest_ai.domain.asset.service;

import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.entity.TradingHistory;
import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.asset.repository.TradingHistoryRepository;
import com.example.invest_ai.global.error.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 주문 체결의 DB 정산(지갑 락 + 잔고/보유량 반영)만 전담하는 서비스.
 *
 * AssetService.executeOrder()는 KIS 외부 REST 호출(최대 수 초)을 이 서비스 밖에서 수행하고,
 * 그 결과가 나온 뒤에만 이 서비스를 호출한다 — DB 행 락(PESSIMISTIC_WRITE)이 네트워크 I/O 동안
 * 유지되는 것을 막기 위함이다.
 *
 * 모든 메서드를 REQUIRES_NEW로 별도 트랜잭션에 태워, ConditionMatchingEngine처럼 여러 조건을
 * 하나의 트랜잭션에서 순회하며 호출하는 상위 호출부가 있더라도, 한 주문의 실패/롤백이 다른
 * 주문의 이미 커밋된 정산 결과에 영향을 주지 않도록 격리한다. (Spring AOP는 self-invocation을
 * 가로채지 못하므로 AssetService와 별도 빈으로 분리했다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSettlementService {

    private final WalletService walletService;
    private final HoldingService holdingService;
    private final TradingHistoryRepository tradingHistoryRepository;

    /** PENDING 이력 생성 — KIS 호출 전에 먼저 커밋해 둔다 (clinerules.md §4.2). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TradingHistory createPendingHistory(Long userId, Long conditionId, String stockCode, String orderType) {
        TradingHistory history = TradingHistory.builder()
                .userId(userId).conditionId(conditionId).stockCode(stockCode).orderType(orderType).build();
        return tradingHistoryRepository.save(history);
    }

    /** KIS 주문 API 실패 시 이력을 FAILED로 마감. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TradingHistory markFailed(Long historyId, String reason) {
        TradingHistory history = tradingHistoryRepository.findById(historyId).orElseThrow();
        history.markFailed(reason);
        return tradingHistoryRepository.save(history);
    }

    /**
     * KIS 체결 성공 후 호출 — 지갑 행을 잠그고 잔고/보유량을 반영한다.
     * executionPrice는 LIMIT 주문이면 지정가, MARKET 주문이면 체결 시점의 시장가여야 한다
     * (호출부인 AssetService에서 이미 확정해서 넘겨준다).
     *
     * 잔고/보유량 최종 검증은 여기(락 보유 시점)에서만 신뢰할 수 있다 — KIS 호출 전의 사전 검증은
     * 락 없이 수행되므로 동시 주문 경합 시 낙관적일 뿐이다. 이 최종 검증에서 실패하면(극히 드문
     * 동시성 경합) KIS 쪽은 이미 체결된 상태이므로 이력을 FAILED로 남기고 별도 조치가 필요함을 로그로 남긴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TradingHistory settleFilledOrder(Long userId, Long historyId, String orderType, String stockCode,
                                             int quantity, BigDecimal executionPrice) {
        TradingHistory history = tradingHistoryRepository.findById(historyId).orElseThrow();
        Wallet wallet = walletService.findByUserIdForUpdate(userId);

        try {
            if ("BUY".equals(orderType)) {
                BigDecimal totalCost = executionPrice.multiply(BigDecimal.valueOf(quantity));
                wallet.withdraw(totalCost);

                Holding holding = holdingService.findByUserIdAndStockCode(userId, stockCode).orElse(null);
                if (holding != null) {
                    holding.increaseQuantity(quantity, executionPrice);
                    holdingService.save(holding);
                } else {
                    holding = Holding.builder().userId(userId).stockCode(stockCode)
                            .quantity(quantity).averagePrice(executionPrice).build();
                    holdingService.save(holding);
                }
            } else {
                Holding holding = holdingService.findOrThrow(userId, stockCode);
                holding.decreaseQuantity(quantity);
                if (holding.getQuantity() == 0) {
                    holdingService.delete(holding);
                } else {
                    holdingService.save(holding);
                }
                BigDecimal proceeds = executionPrice.multiply(BigDecimal.valueOf(quantity));
                wallet.deposit(proceeds);
            }
        } catch (CustomException e) {
            log.error("체결 후 DB 정산 실패 (KIS는 이미 체결 완료 — 수동 확인 필요): userId={}, historyId={}, "
                    + "stockCode={}, orderType={}, reason={}", userId, historyId, stockCode, orderType, e.getMessage());
            history.markFailed("정산 실패: " + e.getMessage() + " (동시 주문 경합 가능성 — 수동 확인 필요)");
            return tradingHistoryRepository.save(history);
        }

        walletService.save(wallet);
        history.markFilled(executionPrice, quantity);
        return tradingHistoryRepository.save(history);
    }
}
