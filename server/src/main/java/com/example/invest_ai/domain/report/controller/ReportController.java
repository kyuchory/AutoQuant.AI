package com.example.invest_ai.domain.report.controller;

import com.example.invest_ai.config.RabbitMqConfig;
import com.example.invest_ai.domain.report.dto.ReportDto;
import com.example.invest_ai.domain.report.service.ReportService;
import com.example.invest_ai.global.common.ApiResponse;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.global.jwt.JwtProvider;
import com.example.invest_ai.infra.config.RedisKeys;
import com.example.invest_ai.infra.rabbitmq.ReportMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * AI 투자 리포트 Controller (api.md §5)
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final RabbitTemplate rabbitTemplate;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping("/stocks/{stockCode}")
    public ApiResponse<ReportDto> getReport(
            @PathVariable String stockCode,
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        ReportDto report = reportService.getReport(userId, stockCode);
        if (report == null) {
            // 전역 예외 처리 규칙(clinerules.md §2.6)에 맞춰 CustomException으로 통일.
            // 응답 code 필드는 ErrorCode enum 이름(REPORT_NOT_FOUND)이며, api.md의 예시 E-코드(E4041)는
            // 실제 GlobalExceptionHandler 구현과 다른 문서상 placeholder였다 (api.md §0 참고).
            throw new CustomException(ErrorCode.REPORT_NOT_FOUND, "아직 생성된 리포트가 없습니다.");
        }
        return new ApiResponse<>(true, "S0000", "OK", report);
    }

    @PostMapping("/stocks/{stockCode}/refresh")
    public ApiResponse<Map<String, String>> refreshReport(
            @PathVariable String stockCode,
            HttpServletRequest request) {
        // 동일 종목 중복 새로고침 방지 (api.md §5.4 E4290, redisflow.md §2.9)
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeys.rateReportRefresh(stockCode), "1", Duration.ofSeconds(30));
        if (Boolean.FALSE.equals(acquired)) {
            throw new CustomException(ErrorCode.REPORT_REFRESH_RATE_LIMITED);
        }

        Long userId = extractUserId(request);
        String requestId = UUID.randomUUID().toString().substring(0, 6);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME,
                "report." + stockCode,
                new ReportMessage(stockCode, userId));
        return new ApiResponse<>(true, "S0000", "분석 요청이 접수되었습니다.",
                Map.of("requestId", requestId, "status", "ACCEPTED"));
    }

    /** Authorization 헤더에서 JWT를 추출해 userId를 반환한다 (AuthController와 동일 패턴) */
    private Long extractUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtProvider.validate(token)) {
                return jwtProvider.getUserId(token);
            }
        }
        // 토큰이 없거나 유효하지 않으면 null 반환 (Worker에서 처리)
        return null;
    }
}