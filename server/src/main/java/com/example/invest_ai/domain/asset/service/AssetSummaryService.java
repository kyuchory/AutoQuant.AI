package com.example.invest_ai.domain.asset.service;

import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 총자산 조회 유스케이스 — WalletService + HoldingService 조합
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetSummaryService {

    private final WalletService walletService;
    private final HoldingService holdingService;
    private final StockRepository stockRepository;
    private final RedisPriceClient redisPriceClient;

    public AssetSummaryResponse getAssetSummary(Long userId) {
        Wallet wallet = walletService.findByUserId(userId);
        List<Holding> holdings = holdingService.findAllByUserId(userId);

        // 종목별 현재가 조회
        List<String> stockCodes = holdings.stream().map(Holding::getStockCode).collect(Collectors.toList());
        Map<String, BigDecimal> priceMap = redisPriceClient.getAllCurrentPrices(stockCodes);

        // stockCode → stockName 매핑
        Map<String, String> stockNameMap = stockRepository.findAllById(stockCodes).stream()
                .collect(Collectors.toMap(Stock::getStockCode, Stock::getStockName));

        List<HoldingInfo> holdingInfos = new ArrayList<>();
        BigDecimal totalEvaluation = BigDecimal.ZERO;
        BigDecimal totalPurchase = BigDecimal.ZERO;

        for (Holding h : holdings) {
            BigDecimal currentPrice = priceMap.getOrDefault(h.getStockCode(), BigDecimal.ZERO);
            BigDecimal evaluation = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
            BigDecimal purchase = h.getAveragePrice().multiply(BigDecimal.valueOf(h.getQuantity()));

            BigDecimal profitLossRate = BigDecimal.ZERO;
            if (h.getAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
                profitLossRate = currentPrice.subtract(h.getAveragePrice())
                        .divide(h.getAveragePrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            totalEvaluation = totalEvaluation.add(evaluation);
            totalPurchase = totalPurchase.add(purchase);

            holdingInfos.add(new HoldingInfo(
                    h.getStockCode(),
                    stockNameMap.getOrDefault(h.getStockCode(), h.getStockCode()),
                    h.getQuantity(), h.getAveragePrice(), currentPrice, evaluation, profitLossRate));
        }

        BigDecimal totalProfitLossAmount = totalEvaluation.subtract(totalPurchase);
        BigDecimal totalProfitLossRate = BigDecimal.ZERO;
        if (totalPurchase.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitLossRate = totalProfitLossAmount
                    .divide(totalPurchase, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new AssetSummaryResponse(
                wallet.getBalance(), totalEvaluation, totalProfitLossAmount, totalProfitLossRate, holdingInfos);
    }
}