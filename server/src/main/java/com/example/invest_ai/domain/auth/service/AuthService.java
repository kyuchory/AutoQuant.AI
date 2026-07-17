package com.example.invest_ai.domain.auth.service;

import com.example.invest_ai.domain.auth.dto.AuthDto.*;
import com.example.invest_ai.domain.user.entity.User;
import com.example.invest_ai.domain.user.repository.UserRepository;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.global.jwt.JwtProvider;
import com.example.invest_ai.infrastructure.kakao.KakaoAuthClient;
import com.example.invest_ai.infrastructure.kakao.KakaoAuthClient.KakaoTokenResponse;
import com.example.invest_ai.infrastructure.kakao.KakaoAuthClient.KakaoUserInfoResponse;
import com.example.invest_ai.infrastructure.redis.RedisAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth 인증 서비스 (api.md §2)
 *
 * 카카오 인가 코드 → 카카오 Access Token → 사용자 정보 → DB 저장/조회 → JWT 발급
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final JwtProvider jwtTokenProvider;
    private final RedisAuthClient redisAuthClient;
    private final UserRepository userRepository;

    /**
     * POST /api/v1/auth/login — 카카오 OAuth 로그인
     */
    @Transactional
    public LoginResponse login(String code) {
        log.info("→ AuthService.login()");

        if (!"KAKAO".equals(code) && code != null) { /* 실제 code값 사용 */ }
        // 1. 인가 코드 → 카카오 Access Token
        KakaoTokenResponse token = kakaoAuthClient.exchangeToken(code);

        // 2. 카카오 사용자 정보 조회
        KakaoUserInfoResponse kakaoUser = kakaoAuthClient.getUserInfo(token.accessToken());

        // 3. DB에서 사용자 조회 or 신규 가입
        User user = findOrCreateUser(kakaoUser);
        boolean isNewUser = user.getCreatedAt().plusMinutes(1).isAfter(java.time.LocalDateTime.now());

        // 4. JWT 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        // 5. Redis에 Refresh Token 저장 (14일)
        redisAuthClient.saveRefreshToken(user.getUserId(), refreshToken);

        UserInfo userInfo = new UserInfo(user.getUserId(), user.getNickname(), user.getEmail());
        log.info("← 로그인 성공: userId={}, isNewUser={}", user.getUserId(), isNewUser);
        return new LoginResponse(accessToken, 3600, isNewUser, userInfo);
    }

    /**
     * POST /api/v1/auth/refresh — Access Token 재발급 (Rotation)
     */
    @Transactional
    public RefreshResponse refresh(String refreshToken) {
        log.info("→ AuthService.refresh()");

        if (!jwtTokenProvider.validate(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // Redis 값과 비교 (탈취 검증)
        String storedToken = redisAuthClient.getRefreshToken(userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 새 Access Token + Refresh Token 발급
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        redisAuthClient.saveRefreshToken(userId, newRefreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        UserInfo userInfo = new UserInfo(user.getUserId(), user.getNickname(), user.getEmail());

        log.info("← Access Token 재발급 완료: userId={}", userId);
        return new RefreshResponse(newAccessToken, 3600, userInfo);
    }

    /**
     * POST /api/v1/auth/logout
     */
    public void logout(String accessToken) {
        Long userId = jwtTokenProvider.getUserId(accessToken);
        String jti = jwtTokenProvider.getJti(accessToken);
        long remainingSeconds = jwtTokenProvider.getRemainingSeconds(accessToken);

        // 블랙리스트 등록 + Refresh Token 삭제
        redisAuthClient.addBlacklist(jti, remainingSeconds);
        redisAuthClient.deleteRefreshToken(userId);

        log.info("← 로그아웃 완료: userId={}", userId);
    }

    /** DB에서 사용자 조회 및 신규 가입 */
    private User findOrCreateUser(KakaoUserInfoResponse kakaoUser) {
        return userRepository.findByProviderAndProviderId("KAKAO", kakaoUser.providerId())
                .map(existingUser -> {
                    if (!existingUser.getNickname().equals(kakaoUser.nickname())) {
                        existingUser.updateNickname(kakaoUser.nickname());
                        userRepository.save(existingUser);
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(kakaoUser.email())
                            .nickname(kakaoUser.nickname())
                            .provider("KAKAO")
                            .providerId(kakaoUser.providerId())
                            .build();
                    User saved = userRepository.save(newUser);
                    log.info("✅ 신규 회원가입: userId={}, email={}", saved.getUserId(), saved.getEmail());
                    return saved;
                });
    }
}