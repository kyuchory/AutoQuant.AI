package com.example.invest_ai.domain.stock.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 시스템 종목 마스터 테이블 (database.md §② stocks)
 *
 * 실시간으로 추적할 종목 코드를 관리하는 기준 테이블.
 * KIS Websocket 구독 목록과 네이버 뉴스 검색 키워드 모두 이 테이블 기준.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(name = "stock_code", length = 10)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 50)
    private String stockName;

    @Column(name = "is_monitored")
    private Boolean isMonitored = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Stock(String stockCode, String stockName, Boolean isMonitored) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.isMonitored = isMonitored != null ? isMonitored : true;
        this.createdAt = LocalDateTime.now();
    }
}