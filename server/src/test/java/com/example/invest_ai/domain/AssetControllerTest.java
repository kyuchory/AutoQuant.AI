package com.example.invest_ai.domain;

import com.example.invest_ai.domain.asset.controller.AssetController;
import com.example.invest_ai.domain.asset.dto.AssetDto.*;
import com.example.invest_ai.domain.asset.service.AssetService;
import com.example.invest_ai.domain.asset.service.AssetSummaryService;
import com.example.invest_ai.global.common.ApiResponse;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AssetControllerTest {

    private static final Long USER_ID = 1L;
    private AssetSummaryService assetSummaryService;
    private AssetService assetService;
    private AssetController controller;

    @BeforeEach
    void setUp() {
        assetSummaryService = mock(AssetSummaryService.class);
        assetService = mock(AssetService.class);
        controller = new AssetController(assetSummaryService, assetService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    @DisplayName("getAssets: 정상 응답 반환")
    void getAssets_정상_응답반환() {
        AssetSummaryResponse resp = new AssetSummaryResponse(new BigDecimal("10000000.0000"),
                new BigDecimal("795000.0000"), new BigDecimal("45000.0000"), new BigDecimal("6.00"),
                List.of(new HoldingInfo("005930", "삼성전자", 10, new BigDecimal("75000.0000"),
                        new BigDecimal("79500.0000"), new BigDecimal("795000.0000"), new BigDecimal("6.00"))));
        given(assetSummaryService.getAssetSummary(USER_ID)).willReturn(resp);
        ApiResponse<AssetSummaryResponse> result = controller.getAssets();
        assertThat(result.success()).isTrue();
        assertThat(result.data().holdings()).hasSize(1);
    }

    @Test
    @DisplayName("getAssets: E4041 예외 전파")
    void getAssets_지갑없음_E4041발생() {
        given(assetSummaryService.getAssetSummary(USER_ID)).willThrow(new CustomException(ErrorCode.ASSET_NOT_FOUND));
        assertThatThrownBy(() -> controller.getAssets()).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("createOrder: BUY 정상 응답")
    void createOrder_BUY_정상_응답반환() {
        OrderRequest req = new OrderRequest("005930", "BUY", 3);
        OrderResponse resp = new OrderResponse(101L, "005930", "BUY", "FILLED", new BigDecimal("79500.0000"), 3, new BigDecimal("238500.0000"), null, "2026-07-21T12:00:00", "2026-07-21T12:00:00");
        given(assetService.executeOrder(eq(USER_ID), any())).willReturn(resp);
        ApiResponse<OrderResponse> result = controller.createOrder(req);
        assertThat(result.success()).isTrue();
        assertThat(result.data().historyId()).isEqualTo(101);
    }

    @Test
    @DisplayName("createOrder: 잔고부족 E4001 예외 전파")
    void createOrder_잔고부족_E4001발생() {
        OrderRequest req = new OrderRequest("005930", "BUY", 999);
        given(assetService.executeOrder(eq(USER_ID), any())).willThrow(new CustomException(ErrorCode.INSUFFICIENT_BALANCE));
        assertThatThrownBy(() -> controller.createOrder(req)).isInstanceOf(CustomException.class);
    }
}