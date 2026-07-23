package com.example.invest_ai.domain.report.service;

import com.example.invest_ai.domain.report.dto.ReportDto;
import com.example.invest_ai.domain.report.entity.AiInvestmentReport;
import com.example.invest_ai.domain.report.repository.AiInvestmentReportRepository;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.infra.config.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * AI 투자 리포트 조회 서비스 (clinerules §2.2)
 *
 * Redis 캐시 우선 조회, 캐시 미스 시 DB 조회 후 반환.
 * 신규 생성은 ReportWorker가 담당하므로 이 서비스는 조회만 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final RedisTemplate<String, String> redisTemplate;
    private final AiInvestmentReportRepository reportRepository;
    private final StockRepository stockRepository;

    public ReportDto getReport(java.lang.Long userId, String stockCode) {
        // Redis 캐시 우선 조회 (§redisflow 2.2)
        String cacheKey = RedisKeys.reportText(stockCode);
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            log.debug("[리포트] Redis 캐시 히트: {}", cacheKey);
            // 캐시에는 reportContent만 저장되므로 간단한 응답 구성
            Stock stock = stockRepository.findById(stockCode).orElse(null);
            String stockName = stock != null ? stock.getStockName() : stockCode;
            return new ReportDto(null, stockCode, stockName, cached, null, true);
        }

        // 캐시 미스 → DB 조회 (§api.md 5.1)
        Optional<AiInvestmentReport> latest = reportRepository
                .findTopByStockStockCodeOrderByCreatedAtDesc(stockCode);

        return latest
                .map(r -> ReportDto.fromEntity(r, false))
                .orElse(null);
    }
}