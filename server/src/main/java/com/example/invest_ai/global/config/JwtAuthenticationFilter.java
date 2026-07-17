package com.example.invest_ai.global.config;

import com.example.invest_ai.global.common.ApiResponse;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.global.jwt.JwtProvider;
import com.example.invest_ai.infrastructure.redis.RedisAuthClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 인증 필터 (api.md §1.3)
 *
 * - /api/v1/auth/login, /api/v1/auth/refresh 는 화이트리스트
 * - 그 외 모든 API는 Authorization: Bearer {token} 헤더 필수
 * - 블랙리스트 확인, SecurityContext 등록
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtTokenProvider;
    private final RedisAuthClient redisAuthClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/ws"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // 화이트리스트 통과
        if (WHITELIST.stream().anyMatch(requestUri::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 토큰 추출 & 검증
        String token = extractToken(request);
        if (token == null || !jwtTokenProvider.validate(token)) {
            sendError(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        // 블랙리스트 확인
        String jti = jwtTokenProvider.getJti(token);
        if (redisAuthClient.isBlacklisted(jti)) {
            sendError(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        // SecurityContext 등록
        Long userId = jwtTokenProvider.getUserId(token);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void sendError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .data(null)
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}