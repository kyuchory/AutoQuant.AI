package com.example.invest_ai.domain.asset.repository;

import com.example.invest_ai.domain.asset.entity.TradingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingHistoryRepository extends JpaRepository<TradingHistory, Long> {
}