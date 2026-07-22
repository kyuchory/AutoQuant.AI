package com.example.invest_ai.domain.news.controller;

import com.example.invest_ai.domain.news.dto.NewsTickerDto;
import com.example.invest_ai.domain.news.service.NewsTickerService;
import com.example.invest_ai.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NewsTickerController {

    private final NewsTickerService newsTickerService;

    @GetMapping("/news/ticker")
    public ApiResponse<List<NewsTickerDto>> getTicker() {
        List<NewsTickerDto> tickers = newsTickerService.getTickerNews();
        return new ApiResponse<>(true, "S0000", "OK", tickers);
    }
}