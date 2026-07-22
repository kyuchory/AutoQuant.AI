package com.example.invest_ai.domain.stock.repository;

import com.example.invest_ai.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, String> {

    List<Stock> findAllByIsMonitoredTrue();
}