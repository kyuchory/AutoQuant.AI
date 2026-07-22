package com.example.invest_ai.domain.asset.service;

import com.example.invest_ai.domain.asset.entity.Holding;
import com.example.invest_ai.domain.asset.repository.HoldingRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingRepository holdingRepository;

    public List<Holding> findAllByUserId(Long userId) {
        return holdingRepository.findAllByUserId(userId);
    }

    public Optional<Holding> findByUserIdAndStockCode(Long userId, String stockCode) {
        return holdingRepository.findByUserIdAndStockCode(userId, stockCode);
    }

    public Holding save(Holding holding) {
        return holdingRepository.save(holding);
    }

    public void delete(Holding holding) {
        holdingRepository.delete(holding);
    }

    /** 보유하지 않은 종목 매도 시 E4001 */
    public Holding findOrThrow(Long userId, String stockCode) {
        return holdingRepository.findByUserIdAndStockCode(userId, stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.HOLDING_NOT_FOUND, "보유하지 않은 종목입니다."));
    }
}