package com.example.invest_ai.kis.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
// @Component  // KIS 테스트 완료 후 OpenAI 테스트를 위해 주석 처리
@RequiredArgsConstructor
public class KisTestRunner implements CommandLineRunner {

    private final KisTestClient kisTestClient;
    private final KisWebsocketTestClient kisWebsocketTestClient;

    @Override
    public void run(String... args) {
        log.info("");
        log.info("======================================================");
        log.info("  KIS Sandbox Test 시작");
        log.info("======================================================");
        log.info("");

        // 1단계: 토큰 발급 (Access Token + Approval Key)
        try {
            kisTestClient.issueTokens();
            log.info("");
        } catch (Exception e) {
            log.error("토큰 발급 테스트 실패: {}", e.getMessage());
        }

        // 2단계: REST 현재가 조회 (삼성전자 005930)
        try {
            kisTestClient.fetchCurrentPrice("005930");
            log.info("");
        } catch (Exception e) {
            log.error("현재가 조회 테스트 실패: {}", e.getMessage());
        }

        // 3단계: WebSocket 실시간 체결가 구독 (삼성전자 005930)
        // 주의: WebSocket은 서버가 push하는 동안 block 상태로 유지됩니다.
        // 종료하려면 Ctrl+C로 프로세스를 중단하세요.
        log.info("======================================================");
        log.info("  WebSocket 실시간 구독 시작 (종료: Ctrl+C)");
        log.info("======================================================");
        log.info("");

        try {
            kisWebsocketTestClient.connectAndSubscribe("005930");
            log.info("");
        } catch (Exception e) {
            log.error("WebSocket 구독 테스트 실패: {}", e.getMessage());
        }

        log.info("======================================================");
        log.info("  KIS Sandbox Test 종료");
        log.info("======================================================");
    }
}