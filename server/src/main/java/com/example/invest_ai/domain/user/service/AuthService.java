package com.example.invest_ai.domain.user.service;

import com.example.invest_ai.domain.user.entity.User;
import com.example.invest_ai.domain.user.repository.UserRepository;
import com.example.invest_ai.global.common.ApiResponse;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infra.jwt.JwtTokenProvider;
import com.example.invest_ai.infra.jwt.KakaoAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth 인증 서비스 (api.md §2 인증)
 *
 * - 카카오 인가 코드 → 카카오 Access Token → 사용자 정보 → DB 저장/조회 → JWT 발급
 * - 응답 포맷은 ApiResponse<T> (clinerules.md §2.1)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * 카카오 OAuth 로그인 (api.md §2.1)
     *
     * @param authorizationCode 카카오 로그인 후 받은 인가 코드
     * @return ApiResponse with accessToken, user info
     */
    @Transactional
    public ApiResponse<Map<String, Object>> kakaoLogin(String authorizationCode) {
        log.info("→ AuthService.kakaoLogin()");

        // 1. 인가 코드 → 카카오 Access Token 발급
        KakaoAuthClient.KakaoTokenResponse tokenResponse = kakaoAuthClient.getAccessToken(authorizationCode);

        // 2. 카카오 Access Token → 사용자 정보 조회
        KakaoAuthClient.KakaoUserInfo userInfo = kakaoAuthClient.getUserInfo(tokenResponse.accessToken());

        // 3. DB에서 사용자 조회 또는 회원가입 (최초 로그인 시 자동 가입)
        User user = findOrCreateUser(userInfo);

        // 4. 우리 서버 JWT 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        // 5. 응답 데이터 조립 (api.md §2.1 응답 포맷)
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("accessTokenExpiresIn", 3600);
        data.put("isNewUser", user.getCreatedAt().plusMinutes(1).isAfter(java.time.LocalDateTime.now()));

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getUserId());
        userData.put("nickname", user.getNickname());
        userData.put("email", user.getEmail());
        data.put("user", userData);

        log.info("← 로그인 성공 (userId: {}, email: {})", user.getUserId(), user.getEmail());
        return ApiResponse.success(data);
    }

    /**
     * Access Token 재발급 (api.md §2.2)
     */
    @Transactional
    public ApiResponse<Map<String, Object>> refreshAccessToken(String refreshToken) {
        log.info("→ AuthService.refreshAccessToken()");

        // 1. Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "E4011");
        }

        // 2. userId 추출
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // 3. 사용자 존재 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 4. 새 Access Token + Refresh Token 발급 (Rotation)
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getUserId());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", newAccessToken);
        data.put("accessTokenExpiresIn", 3600);

        log.info("← Access Token 재발급 성공 (userId: {})", userId);
        return ApiResponse.success(data);
    }

    /**
     * 카카오 사용자 정보로 DB에서 조회하거나 신규 저장 (자동 회원가입)
     */
    private User findOrCreateUser(KakaoAuthClient.KakaoUserInfo userInfo) {
        Optional<User> existingUser = userRepository.findByEmail(userInfo.email());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // 닉네임이 변경되었을 수 있으므로 업데이트
            if (!user.getNickname().equals(userInfo.nickname())) {
                user.updateNickname(userInfo.nickname());
                userRepository.save(user);
            }
            return user;
        }

        // 신규 회원가입 (database.md ① users 테이블)
        User newUser = User.builder()
                .email(userInfo.email())
                .nickname(userInfo.nickname())
                .provider("KAKAO")
                .providerId(userInfo.providerId())
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("✅ 신규 회원가입 (userId: {}, email: {})", savedUser.getUserId(), savedUser.getEmail());
        return savedUser;
    }
}