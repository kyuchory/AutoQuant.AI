package com.example.invest_ai.domain.chart.controller;

import com.example.invest_ai.domain.chart.dto.ChartDto.ChartResponse;
import com.example.invest_ai.domain.chart.service.ChartService;
import com.example.invest_ai.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/charts")
@RequiredArgsConstructor
public class ChartController {

    private final ChartService chartService;

    /** GET /api/v1/charts/{stockCode}/daily?period=D|W|M|Y */
    @GetMapping("/{stockCode}/daily")
    public ApiResponse<ChartResponse> getDailyChart(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "D") String period
    ) {
        return ApiResponse.success(chartService.getDailyChart(stockCode, period));
    }

    /** GET /api/v1/charts/{stockCode}/minute */
    @GetMapping("/{stockCode}/minute")
    public ApiResponse<ChartResponse> getMinuteChart(@PathVariable String stockCode) {
        return ApiResponse.success(chartService.getMinuteChart(stockCode));
    }
}