package com.example.invest_ai.infrastructure.kis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * KIS REST API 호출 전역 레이트리미터.
 *
 * KIS 모의투자는 실전 계좌보다 초당 호출 한도가 낮다. 별도 라이브러리(bucket4j 등) 없이
 * Semaphore + 주기적 permit 재충전 방식의 간단한 leaky-bucket으로 호출량을 제한한다.
 * 모든 KIS REST 호출(차트/시세/주문)은 이 리미터를 거쳐야 한다.
 */
@Slf4j
@Component
public class KisRateLimiter {

    private static final int MAX_PERMITS = 2;
    private static final long ACQUIRE_TIMEOUT_SECONDS = 3;

    private final Semaphore semaphore = new Semaphore(MAX_PERMITS);

    /** 500ms마다 최대 permit까지 재충전 — 초당 최대 약 4건(=2 permit × 2회/초)으로 호출량을 제한 */
    @Scheduled(fixedRate = 500)
    public void refill() {
        int toRelease = MAX_PERMITS - semaphore.availablePermits();
        if (toRelease > 0) {
            semaphore.release(toRelease);
        }
    }

    /** 호출 permit 획득. 대기 한도 초과 시 예외 대신 false를 반환해 호출부가 처리 방식을 선택하게 한다. */
    public boolean acquire() {
        try {
            boolean acquired = semaphore.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("KIS 호출 레이트리밋 대기 초과 ({}초)", ACQUIRE_TIMEOUT_SECONDS);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
