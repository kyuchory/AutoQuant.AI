package com.example.invest_ai.domain.stock.controller;

import com.example.invest_ai.domain.stock.dto.StockDto.StockInfo;
import com.example.invest_ai.domain.stock.service.StockService;
import com.example.invest_ai.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 종목 조회 API (api.md §3 — stocks)
 */
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /** GET /api/v1/stocks — 모니터링 대상 종목 + Redis 현재가 */
    @GetMapping
    public ApiResponse<List<StockInfo>> getStocks() {
        return ApiResponse.success(stockService.getMonitoredStocksWithPrice());
    }
}