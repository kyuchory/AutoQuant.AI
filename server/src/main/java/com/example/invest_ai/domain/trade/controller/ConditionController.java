package com.example.invest_ai.domain.trade.controller;

import com.example.invest_ai.domain.trade.dto.ConditionDto.*;
import com.example.invest_ai.domain.trade.service.ConditionService;
import com.example.invest_ai.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 자동 매매 조건 API (api.md §4)
 */
@RestController
@RequestMapping("/api/v1/conditions")
@RequiredArgsConstructor
public class ConditionController {

    private final ConditionService conditionService;

    /** POST /api/v1/conditions — 조건 등록 */
    @PostMapping
    public ApiResponse<TradingConditionResponse> createCondition(
            @Valid @RequestBody TradingConditionRequest request) {
        Long userId = getCurrentUserId();
        return ApiResponse.created(conditionService.createCondition(userId, request));
    }

    /** GET /api/v1/conditions — 조건 목록 조회 */
    @GetMapping
    public ApiResponse<List<TradingConditionResponse>> getConditions() {
        Long userId = getCurrentUserId();
        return ApiResponse.success(conditionService.getConditions(userId));
    }

    /** PATCH /api/v1/conditions/{conditionId}/active — 조건 감시 ON/OFF 토글 */
    @PatchMapping("/{conditionId}/active")
    public ApiResponse<TradingConditionResponse> updateActive(
            @PathVariable Long conditionId,
            @Valid @RequestBody ActiveUpdateRequest request) {
        Long userId = getCurrentUserId();
        return ApiResponse.success(conditionService.updateActive(userId, conditionId, request.isActive()));
    }

    /** PUT /api/v1/conditions/{conditionId} — 조건 수정 */
    @PutMapping("/{conditionId}")
    public ApiResponse<TradingConditionResponse> updateCondition(
            @PathVariable Long conditionId,
            @Valid @RequestBody TradingConditionRequest request) {
        Long userId = getCurrentUserId();
        return ApiResponse.success(conditionService.updateCondition(userId, conditionId, request));
    }

    /** DELETE /api/v1/conditions/{conditionId} — 조건 삭제 */
    @DeleteMapping("/{conditionId}")
    public ApiResponse<Void> deleteCondition(@PathVariable Long conditionId) {
        Long userId = getCurrentUserId();
        conditionService.deleteCondition(userId, conditionId);
        return ApiResponse.success(null);
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}