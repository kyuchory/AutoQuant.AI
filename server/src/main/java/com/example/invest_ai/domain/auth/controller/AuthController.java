package com.example.invest_ai.domain.auth.controller;

import com.example.invest_ai.domain.auth.dto.AuthDto.*;
import com.example.invest_ai.domain.auth.service.AuthService;
import com.example.invest_ai.global.common.ApiResponse;
import com.example.invest_ai.global.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * 인증 API 컨트롤러 (api.md §2)
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProvider jwtTokenProvider;

    /** POST /api/v1/auth/login — 카카오 OAuth 로그인 */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.login(request.code());

        // Refresh Token → HttpOnly Secure SameSite Strict Cookie
        setRefreshTokenCookie(response, loginResponse.accessToken());
        return ApiResponse.success(loginResponse);
    }

    /** POST /api/v1/auth/refresh — Access Token 재발급 */
    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractRefreshToken(request);
        RefreshResponse refreshResponse = authService.refresh(refreshToken);

        setRefreshTokenCookie(response, refreshResponse.accessToken());
        return ApiResponse.success(refreshResponse);
    }

    /** POST /api/v1/auth/logout */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = extractAccessToken(request);
        authService.logout(token);

        // 쿠키 제거
        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true).secure(false).sameSite("Strict").path("/").maxAge(0).build();
        response.addHeader("Set-Cookie", deleteCookie.toString());
        return ApiResponse.success(null);
    }

    /** Refresh Token 쿠키 설정 */
    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(1209600) // 14일
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** 쿠키에서 Refresh Token 추출 */
    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /** Authorization 헤더에서 Access Token 추출 */
    private String extractAccessToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}