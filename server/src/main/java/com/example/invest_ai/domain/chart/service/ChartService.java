package com.example.invest_ai.domain.chart.service;

import com.example.invest_ai.domain.chart.dto.ChartDto.*;
import com.example.invest_ai.domain.stock.entity.Stock;
import com.example.invest_ai.domain.stock.repository.StockRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infrastructure.kis.KisChartClient;
import com.example.invest_ai.infrastructure.kis.KisChartClient.CandleData;
import com.example.invest_ai.infrastructure.redis.RedisPriceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private final StockRepository stockRepository;
    private final KisChartClient kisChartClient;
    private final RedisPriceClient redisPriceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CACHE_KEY_PREFIX = "chart:";

    /** 일봉/주봉/월봉/연봉 차트 조회 — Redis 캐싱 적용 */
    public ChartResponse getDailyChart(String stockCode, String periodCode) {
        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));

        String cacheKey = CACHE_KEY_PREFIX + stockCode + ":daily:" + periodCode;

        // 1. Redis 캐시 확인
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ChartResponse.class);
            } catch (Exception e) {
                log.warn("차트 캐시 역직렬화 실패: key={}", cacheKey, e);
            }
        }

        // 2. Cache Miss → KIS 호출
        LocalDate today = LocalDate.now(KST);
        LocalDate startDate = switch (periodCode) {
            case "D" -> today.minusDays(150);
            case "W" -> today.minusYears(2);
            case "M" -> today.minusYears(5);
            case "Y" -> today.minusYears(10);
            default -> today.minusDays(150);
        };

        List<CandleData> raw = kisChartClient.getDailyChart(
                stockCode, periodCode,
                startDate.format(DATE_FMT),
                today.format(DATE_FMT));

        List<CandleItem> candles = raw.stream()
                .map(c -> {
                    LocalDate d = LocalDate.parse(c.date(), DATE_FMT);
                    long timestamp = d.atStartOfDay(KST).toEpochSecond();
                    return new CandleItem(timestamp, c.open(), c.high(), c.low(), c.close(), c.volume());
                }).collect(Collectors.toList());

        long currentPrice = fetchCurrentPrice(stockCode);
        long changeAmount = 0;
        double changeRate = 0;

        if (candles.size() >= 2) {
            long prevClose = candles.get(candles.size() - 2).close();
            if (prevClose > 0) {
                changeAmount = currentPrice - prevClose;
                changeRate = (double) changeAmount / prevClose * 100;
            }
        }

        CandleItem latest = candles.isEmpty() ? null : candles.get(candles.size() - 1);
        ChartResponse response = new ChartResponse(
                stockCode, stock.getStockName(), periodCode,
                currentPrice, changeAmount, changeRate,
                latest != null ? latest.open() : 0,
                latest != null ? latest.high() : 0,
                latest != null ? latest.low() : 0,
                candles
        );

        // 3. Redis 저장 (빈 candles는 캐시하지 않음)
        if (!candles.isEmpty()) {
            try {
                Duration ttl = switch (periodCode) {
                    case "D" -> Duration.ofMinutes(10);
                    case "W" -> Duration.ofHours(1);
                    case "M" -> Duration.ofHours(6);
                    case "Y" -> Duration.ofHours(24);
                    default -> Duration.ofMinutes(10);
                };
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), ttl);
            } catch (Exception e) {
                log.warn("차트 캐시 저장 실패: key={}", cacheKey, e);
            }
        }

        return response;
    }

    /** 분봉 차트 조회 (30분 단위 반복 호출 + 장 마감 후 Redis 캐싱) */
    public ChartResponse getMinuteChart(String stockCode) {
        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));

        String cacheKey = CACHE_KEY_PREFIX + stockCode + ":minute";
        LocalTime now = LocalTime.now(KST);
        LocalTime marketClose = LocalTime.of(15, 30);

        if (now.isAfter(marketClose)) {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, ChartResponse.class);
                } catch (Exception e) {
                    log.warn("분봉 캐시 역직렬화 실패", e);
                }
            }
            // 장 마감 후 캐시 미스 → KIS 호출 생략, 빈 응답 반환
            return new ChartResponse(stockCode, stock.getStockName(), "MINUTE",
                    0, 0, 0, 0, 0, 0, List.of());
        }

        List<String> hours = new ArrayList<>();
        LocalTime current = LocalTime.of(Math.min(now.getHour(), 15), 0);
        LocalTime start = LocalTime.of(9, 0);
        while (!current.isBefore(start)) {
            int minute = current.getMinute();
            if (minute < 30) current = current.withMinute(0);
            else current = current.withMinute(30);
            if (current.isBefore(start)) break;
            hours.add(current.format(DateTimeFormatter.ofPattern("HHmmss")));
            current = current.minusMinutes(30);
        }

        Set<String> seen = new HashSet<>();
        List<CandleData> allCandles = new ArrayList<>();
        for (String hour : hours) {
            List<CandleData> batch = kisChartClient.getMinuteChart(stockCode, hour);
            for (CandleData c : batch) {
                if (seen.add(c.time())) allCandles.add(c);
            }
        }

        allCandles.sort(Comparator.comparing(CandleData::time));

        LocalDate today = LocalDate.now(KST);
        List<CandleItem> candles = allCandles.stream()
                .map(c -> {
                    long timestamp = today.atStartOfDay(KST)
                            .plusHours(Long.parseLong(c.time().substring(0, 2)))
                            .plusMinutes(Long.parseLong(c.time().substring(2, 4)))
                            .plusSeconds(Long.parseLong(c.time().substring(4, 6)))
                            .toEpochSecond();
                    return new CandleItem(timestamp, c.open(), c.high(), c.low(), c.close(), c.volume());
                }).collect(Collectors.toList());

        long currentPrice = fetchCurrentPrice(stockCode);
        long changeAmount = 0;
        double changeRate = 0;

        if (candles.size() >= 2) {
            long prevClose = candles.get(candles.size() - 2).close();
            if (prevClose > 0) {
                changeAmount = currentPrice - prevClose;
                changeRate = (double) changeAmount / prevClose * 100;
            }
        }

        CandleItem latest = candles.isEmpty() ? null : candles.get(candles.size() - 1);
        ChartResponse response = new ChartResponse(
                stockCode, stock.getStockName(), "MINUTE",
                currentPrice, changeAmount, changeRate,
                latest != null ? latest.open() : 0,
                latest != null ? latest.high() : 0,
                latest != null ? latest.low() : 0,
                candles
        );

        if (now.isAfter(marketClose)) {
            long sec = LocalDateTime.of(today.plusDays(1), LocalTime.of(9, 0))
                    .atZone(KST).toEpochSecond() - Instant.now().getEpochSecond();
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response),
                        java.time.Duration.ofSeconds(sec));
            } catch (Exception e) {
                log.warn("분봉 캐시 저장 실패", e);
            }
        }

        return response;
    }

    private long fetchCurrentPrice(String stockCode) {
        try {
            return redisPriceClient.getCurrentPrice(stockCode).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }
}