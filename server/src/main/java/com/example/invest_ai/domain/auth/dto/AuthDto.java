package com.example.invest_ai.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 인증 관련 DTO (api.md §2)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthDto {

    /** POST /api/v1/auth/login 요청 */
    public record LoginRequest(String provider, String code) {}

    /** 로그인 응답 */
    public record LoginResponse(
            String accessToken,
            long accessTokenExpiresIn,
            boolean isNewUser,
            UserInfo user,
            String refreshToken
    ) {}

    /** Access Token 재발급 응답 */
    public record RefreshResponse(
            String accessToken,
            long accessTokenExpiresIn,
            UserInfo user,
            String refreshToken
    ) {}

    /** 사용자 정보 */
    public record UserInfo(Long userId, String nickname, String email) {}
}