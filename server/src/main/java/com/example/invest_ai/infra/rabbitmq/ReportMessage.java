package com.example.invest_ai.infra.rabbitmq;

import java.io.Serializable;

/**
 * RabbitMQ report-queue 메시지 래퍼
 *
 * @param stockCode 리포트 생성 대상 종목 코드
 * @param userId    요청한 사용자 ID
 */
public record ReportMessage(
        String stockCode,
        Long userId
) implements Serializable {
}