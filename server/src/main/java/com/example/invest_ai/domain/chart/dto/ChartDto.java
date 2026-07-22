package com.example.invest_ai.domain.chart.dto;

import java.util.List;

public class ChartDto {

    public record CandleItem(
            long time,      // Unix timestamp (초 단위) — lightweight-charts 필수 형식
            long open,
            long high,
            long low,
            long close,
            long volume
    ) {}

    public record ChartResponse(
            String stockCode,
            String stockName,
            String periodCode,
            long currentPrice,
            long changeAmount,
            double changeRate,
            long openPrice,
            long highPrice,
            long lowPrice,
            List<CandleItem> candles
    ) {}
}