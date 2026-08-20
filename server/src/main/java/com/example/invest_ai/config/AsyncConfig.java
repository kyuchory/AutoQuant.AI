package com.example.invest_ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * 조건 매칭 엔진 전용 비동기 실행기.
 *
 * KIS WebSocket이 체결 틱을 수신하는 스레드(Reactor Netty 이벤트 루프)에서
 * {@link com.example.invest_ai.domain.trade.service.ConditionMatchingEngine}의 주문 실행(KIS REST
 * .block() 호출, 최악의 경우 레이트리미터 대기 3초 + HTTP 타임아웃 5초 = 최대 8초)까지 동기로 흘러가면
 * 그 시간 동안 웹소켓 수신 스레드가 막혀 다른 감시 종목의 실시간 시세가 전부 지연된다.
 * 조건 평가/주문 실행을 별도 스레드풀로 넘겨 웹소켓 수신 스레드를 절대 블로킹하지 않도록 한다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String CONDITION_MATCHING_EXECUTOR = "conditionMatchingExecutor";

    @Bean(CONDITION_MATCHING_EXECUTOR)
    public Executor conditionMatchingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cond-match-");
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return conditionMatchingExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("비동기 처리 중 예외 발생: method={}", method.getName(), ex);
    }
}
