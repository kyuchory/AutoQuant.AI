package com.example.invest_ai.domain.asset.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * 자산 관련 DTO (api.md §3)
 */
public class AssetDto {

    /** GET /api/v1/assets 응답 */
    public record AssetSummaryResponse(
            BigDecimal walletBalance,
            BigDecimal totalEvaluationAmount,
            BigDecimal totalProfitLossAmount,
            BigDecimal totalProfitLossRate,
            List<HoldingInfo> holdings
    ) {}

    /** 보유 종목 정보 */
    public record HoldingInfo(
            String stockCode,
            String stockName,
            int quantity,
            BigDecimal averagePrice,
            BigDecimal currentPrice,
            BigDecimal evaluationAmount,
            BigDecimal profitLossRate
    ) {}

    /** POST /api/v1/assets/orders 요청 */
    public record OrderRequest(
            @NotBlank String stockCode,
            @NotBlank String orderType,   // BUY 또는 SELL
            @Min(1) int quantity
    ) {}

    /** 주문 응답 */
    public record OrderResponse(
            Long historyId,
            String stockCode,
            String orderType,
            String status,
            BigDecimal executionPrice,
            Integer executionQuantity,
            BigDecimal totalAmount,
            String failureReason,
            String requestedAt,
            String executedAt
    ) {}
}