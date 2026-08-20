package com.example.invest_ai.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 인증 관련 DTO (api.md §2)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthDto {

    /** POST /api/v1/auth/login 요청 */
    public record LoginRequest(String provider, String code) {}

    /**
     * 로그인 응답.
     * refreshToken은 AuthController가 Set-Cookie(HttpOnly)로만 전달하는 내부 값이며,
     * api.md §2.1 / clinerules.md §7.4에 따라 응답 바디에 노출하지 않는다(@JsonIgnore).
     */
    public record LoginResponse(
            String accessToken,
            long accessTokenExpiresIn,
            boolean isNewUser,
            UserInfo user,
            @JsonIgnore String refreshToken
    ) {}

    /** Access Token 재발급 응답 (refreshToken 바디 미노출 사유는 LoginResponse와 동일) */
    public record RefreshResponse(
            String accessToken,
            long accessTokenExpiresIn,
            UserInfo user,
            @JsonIgnore String refreshToken
    ) {}

    /** 사용자 정보 */
    public record UserInfo(Long userId, String nickname, String email) {}
}