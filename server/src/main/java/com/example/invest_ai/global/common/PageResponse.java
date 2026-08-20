package com.example.invest_ai.global.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 공통 페이징 응답 래퍼 (api.md §1.5)
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
