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

    /**
     * 주문 처리용 — 행 잠금(PESSIMISTIC_WRITE)으로 Wallet 조회.
     * 트랜잭션 내에서 반드시 가장 먼저 호출해 동시 주문 간 잔고 경쟁을 직렬화한다.
     */
    public Wallet findByUserIdForUpdate(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND, "지갑 정보가 없습니다."));
    }

    public void save(Wallet wallet) {
        walletRepository.save(wallet);
    }
}