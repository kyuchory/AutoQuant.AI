package com.example.invest_ai.domain.asset.repository;

import com.example.invest_ai.domain.asset.entity.TradingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradingHistoryRepository extends JpaRepository<TradingHistory, Long> {

    /** GET /api/v1/assets/histories — stockCode/status는 선택 필터 (api.md §3.3) */
    @Query("""
            SELECT h FROM TradingHistory h
            WHERE h.userId = :userId
              AND (:stockCode IS NULL OR h.stockCode = :stockCode)
              AND (:status IS NULL OR h.status = :status)
            ORDER BY h.requestedAt DESC
            """)
    Page<TradingHistory> findHistories(
            @Param("userId") Long userId,
            @Param("stockCode") String stockCode,
            @Param("status") String status,
            Pageable pageable);
}