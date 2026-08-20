package com.example.invest_ai.domain.asset.controller;

import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.service.AssetService;
import com.example.invest_ai.domain.asset.service.AssetSummaryService;
import com.example.invest_ai.global.common.ApiResponse;
import com.example.invest_ai.global.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    /** GET /api/v1/assets/histories — 매매 체결 이력 페이징 조회 (api.md §3.3) */
    @GetMapping("/histories")
    public ApiResponse<PageResponse<OrderResponse>> getHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String status) {
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(assetService.getHistories(userId, stockCode, status, pageable));
    }

    /** SecurityContext에서 userId 추출 */
    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}