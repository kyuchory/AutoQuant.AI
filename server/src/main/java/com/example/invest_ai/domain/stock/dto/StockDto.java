package com.example.invest_ai.domain.stock.dto;

import java.math.BigDecimal;

/**
 * 종목 관련 DTO
 */
public class StockDto {

    public record StockInfo(
            String stockCode,
            String stockName,
            BigDecimal currentPrice
    ) {}
}