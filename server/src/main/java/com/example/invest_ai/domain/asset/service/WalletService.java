package com.example.invest_ai.domain.asset.service;

import com.example.invest_ai.domain.asset.entity.Wallet;
import com.example.invest_ai.domain.asset.repository.WalletRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    /** userId로 Wallet 조회 — 없으면 E4041 */
    public Wallet findByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND, "지갑 정보가 없습니다."));
    }

    public void save(Wallet wallet) {
        walletRepository.save(wallet);
    }
}