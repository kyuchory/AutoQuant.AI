package com.example.invest_ai.domain.asset.controller;

import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.service.AssetService;
import com.example.invest_ai.domain.asset.service.AssetSummaryService;
import com.example.invest_ai.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 자산 API (api.md §3)
 */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetSummaryService assetSummaryService;
    private final AssetService assetService;

    /** GET /api/v1/assets — 자산 종합 조회 */
    @GetMapping
    public ApiResponse<AssetSummaryResponse> getAssets() {
        Long userId = getCurrentUserId();
        return ApiResponse.success(assetSummaryService.getAssetSummary(userId));
    }

    /** POST /api/v1/assets/orders — 수동 매매 주문 */
    @PostMapping("/orders")
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        Long userId = getCurrentUserId();
        return ApiResponse.success(assetService.executeOrder(userId, request));
    }

    /** SecurityContext에서 userId 추출 */
    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}