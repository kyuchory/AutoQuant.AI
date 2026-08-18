package com.example.invest_ai.domain.trade.repository;

import com.example.invest_ai.domain.trade.entity.TradingCondition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * trading_conditions 테이블 Repository
 */
public interface TradingConditionRepository extends JpaRepository<TradingCondition, Long> {

    /** 특정 유저의 활성 조건 목록 (조건 매칭 워커용, 트리거 함께 조회) */
    @EntityGraph(attributePaths = "triggers")
    List<TradingCondition> findAllByUserIdAndIsActiveTrue(Long userId);

    /** 특정 유저의 모든 조건 목록 (활성/비활성 무관, 목록 조회용) */
    @EntityGraph(attributePaths = "triggers")
    List<TradingCondition> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /** 특정 유저의 조건 단건 (소유권 검증용) */
    Optional<TradingCondition> findByConditionIdAndUserId(Long conditionId, Long userId);

    /** 특정 종목의 활성 조건 목록 (조건 매칭 워커: 시세 이벤트 발생 종목 기준) */
    @EntityGraph(attributePaths = "triggers")
    List<TradingCondition> findAllByStockCodeAndIsActiveTrue(String stockCode);
}