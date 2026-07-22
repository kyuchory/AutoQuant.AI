package com.example.invest_ai.domain.asset.repository;

import com.example.invest_ai.domain.asset.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findAllByUserId(Long userId);

    Optional<Holding> findByUserIdAndStockCode(Long userId, String stockCode);
}