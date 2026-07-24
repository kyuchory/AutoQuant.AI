package com.example.invest_ai.domain.asset.service;

import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.entity.TradingHistory;
import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.asset.repository.TradingHistoryRepository;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infrastructure.kis.KisOrderClient;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 주문 실행 서비스 — WalletService + HoldingService에 위임
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final WalletService walletService;
    private final HoldingService holdingService;
    private final TradingHistoryRepository tradingHistoryRepository;
    private final StockRepository stockRepository;
    private final RedisPriceClient redisPriceClient;
    private final KisOrderClient kisOrderClient;

    /** POST /api/v1/assets/orders — 수동 매매 주문 */
    @Transactional
    public OrderResponse executeOrder(Long userId, OrderRequest request) {
        String stockCode = request.stockCode();
        String orderType = request.orderType().toUpperCase();
        int quantity = request.quantity();
        String ordDvsn = request.ordDvsn();
        BigDecimal orderPrice = request.price();

        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND, "종목이 존재하지 않습니다: " + stockCode));

        BigDecimal currentPrice = redisPriceClient.getCurrentPrice(stockCode);
        Wallet wallet = walletService.findByUserId(userId);

        if ("BUY".equals(orderType)) {
            BigDecimal totalCost = currentPrice.multiply(BigDecimal.valueOf(quantity));
            wallet.withdraw(totalCost);

            Holding holding = holdingService.findByUserIdAndStockCode(userId, stockCode).orElse(null);
            if (holding != null) {
                holding.increaseQuantity(quantity, currentPrice);
                holdingService.save(holding);
            } else {
                holding = Holding.builder().userId(userId).stockCode(stockCode)
                        .quantity(quantity).averagePrice(currentPrice).build();
                holdingService.save(holding);
            }
        } else if ("SELL".equals(orderType)) {
            Holding holding = holdingService.findOrThrow(userId, stockCode);
            holding.decreaseQuantity(quantity);
            if (holding.getQuantity() == 0) {
                holdingService.delete(holding);
            } else {
                holdingService.save(holding);
            }
            BigDecimal proceeds = currentPrice.multiply(BigDecimal.valueOf(quantity));
            wallet.deposit(proceeds);
        } else {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "orderType은 BUY 또는 SELL이어야 합니다.");
        }

        walletService.save(wallet);

        TradingHistory history = TradingHistory.builder()
                .userId(userId).stockCode(stockCode).orderType(orderType).build();
        history.markFilled(currentPrice, quantity);
        tradingHistoryRepository.save(history);

        try {
            kisOrderClient.executeOrder(stockCode, orderType, quantity, ordDvsn, orderPrice);
        } catch (Exception e) {
            log.error("KIS 주문 API 실패: {}", e.getMessage());
            history.markFailed(e.getMessage());
            tradingHistoryRepository.save(history);
        }

        return new OrderResponse(
                history.getHistoryId(), history.getStockCode(), history.getOrderType(),
                history.getStatus(), history.getExecutionPrice(), history.getExecutionQuantity(),
                history.getTotalAmount(), history.getFailureReason(),
                history.getRequestedAt().toString(),
                history.getExecutedAt() != null ? history.getExecutedAt().toString() : null);
    }
}