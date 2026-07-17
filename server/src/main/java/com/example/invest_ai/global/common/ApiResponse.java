package com.example.invest_ai.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * 공통 응답 포맷 (api.md §1.2)
 *
 * 성공: { "success": true, "code": "S0000", "message": "OK", "data": { } }
 * 실패: { "success": false, "code": "E4001", "message": "...", "data": null }
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("S0000")
                .message("OK")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("S0000")
                .message("OK")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(null)
                .build();
    }
}