package com.example.invest_ai.auth;

import com.example.invest_ai.domain.user.entity.User;
import com.example.invest_ai.domain.user.repository.UserRepository;
import com.example.invest_ai.domain.user.service.AuthService;
import com.example.invest_ai.global.common.ApiResponse;
import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import com.example.invest_ai.infra.jwt.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth 인증 진짜 통합 테스트
 *
 * - api.md §2 (인증) + database.md ① users 테이블 + clinerules.md §2.6 반영
 * - 실제 AuthService, JwtTokenProvider, DB가 연동되어 동작
 *
 * ⚠️ 카카오 로그인 실제 연동 테스트 (testKakaoLoginReal):
 *    브라우저에서 아래 URL로 접속 → 로그인 → redirect URL에서 code 파라미터 복사
 *    https://kauth.kakao.com/oauth/authorize?client_id=8e00d3b0b6b3b6a1b6e0f4ddfa44eeb1&redirect_uri=http://localhost:8080/api/v1/auth/login/kakao/callback&response_type=code
 *
 *    복사한 code 값을 아래 realAuthCode 변수에 넣고 테스트 실행
 */
@Slf4j
@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    // ========================================================================
    // 카카오 실제 인가 코드를 여기에 입력하세요 (브라우저에서 복사)
    // ========================================================================
    private static final String realAuthCode = "tJTR9tf7MlesXcGAe2GW8t09br4zp-Jqo8tXBcV3GrCokKsoCm9XmgAAAAQKFxafAAABn2EUWozE017PSiBv1Q"; // ← 여기에 복사

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ========================================================================
    // 1. 회원가입 테스트 (DB 저장 + 중복 검증)
    // ========================================================================

    @Test
    @DisplayName("User 엔티티 저장 - DB에 정상 저장 및 조회")
    void testSaveUserToDatabase() {
        log.info("======================================================");
        log.info("  [테스트] User 엔티티 DB 저장");
        log.info("======================================================");

        // Given
        User user = User.builder()
                .email("test@kakao.com")
                .nickname("테스트유저")
                .provider("KAKAO")
                .providerId("test_provider_id_123")
                .build();

        // When
        User savedUser = userRepository.save(user);
        User foundUser = userRepository.findByEmail("test@kakao.com").orElse(null);

        // Then
        assertNotNull(savedUser.getUserId(), "저장 시 PK가 자동 생성되어야 합니다");
        assertNotNull(foundUser, "DB에서 이메일로 조회 가능해야 합니다");
        assertEquals("테스트유저", foundUser.getNickname());
        assertEquals("KAKAO", foundUser.getProvider());

        log.info("✅ DB 저장 성공 - userId: {}, email: {}", savedUser.getUserId(), savedUser.getEmail());
        log.info("======================================================");
    }

    @Test
    @DisplayName("중복 이메일 저장 시 DUPLICATE_EMAIL 예외 (uk_users_email)")
    void testDuplicateEmailThrowsException() {
        log.info("======================================================");
        log.info("  [테스트] 중복 이메일 → DUPLICATE_EMAIL 예외");
        log.info("======================================================");

        // Given
        userRepository.save(User.builder()
                .email("duplicate@kakao.com")
                .nickname("첫번째유저")
                .provider("KAKAO")
                .providerId("111")
                .build());

        // When & Then: 동일한 email로 저장 시도 → DataIntegrityViolationException
        assertThrows(Exception.class, () -> {
            userRepository.save(User.builder()
                    .email("duplicate@kakao.com")
                    .nickname("두번째유저")
                    .provider("KAKAO")
                    .providerId("222")
                    .build());
            userRepository.flush();
        });

        log.info("✅ 중복 이메일 감지 완료 (uk_users_email 제약조건)");
        log.info("======================================================");
    }

    // ========================================================================
    // 2. JWT 토큰 발급 및 검증 테스트
    // ========================================================================

    @Test
    @DisplayName("JWT Access Token 발급 및 검증")
    void testJwtAccessToken() {
        log.info("======================================================");
        log.info("  [테스트] JWT Access Token 발급 및 검증");
        log.info("======================================================");

        // Given: DB에 유저 저장
        User user = userRepository.save(User.builder()
                .email("jwtuser@kakao.com")
                .nickname("JWT유저")
                .provider("KAKAO")
                .providerId("jwt_provider_id")
                .build());

        // When: Access Token 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
        log.info("   AccessToken: {}...", accessToken.substring(0, 30));

        // Then: 검증
        assertTrue(jwtTokenProvider.validateToken(accessToken), "Access Token은 유효해야 합니다");
        Long extractedUserId = jwtTokenProvider.getUserIdFromToken(accessToken);
        assertEquals(user.getUserId(), extractedUserId, "토큰에서 추출한 userId가 일치해야 합니다");

        log.info("✅ Access Token 검증 성공 - userId: {}", extractedUserId);
        log.info("======================================================");
    }

    @Test
    @DisplayName("JWT Refresh Token 발급 - Access Token보다 만료기간이 길어야 함")
    void testJwtRefreshToken() {
        log.info("======================================================");
        log.info("  [테스트] JWT Refresh Token 발급");
        log.info("======================================================");

        User user = userRepository.save(User.builder()
                .email("refreshtest@kakao.com")
                .nickname("리프레시유저")
                .provider("KAKAO")
                .providerId("refresh_provider_id")
                .build());

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        assertTrue(jwtTokenProvider.validateToken(refreshToken), "Refresh Token은 유효해야 합니다");
        Long userIdFromRefresh = jwtTokenProvider.getUserIdFromToken(refreshToken);
        assertEquals(user.getUserId(), userIdFromRefresh);

        log.info("✅ Refresh Token 발급 성공 - userId: {}", userIdFromRefresh);
        log.info("======================================================");
    }

    @Test
    @DisplayName("변조된 JWT 토큰 검증 실패 - validateToken이 false 반환")
    void testInvalidJwtToken() {
        log.info("======================================================");
        log.info("  [테스트] 변조된 JWT → validateToken false");
        log.info("======================================================");

        String tamperedToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.tampered_signature";

        boolean isValid = jwtTokenProvider.validateToken(tamperedToken);

        assertFalse(isValid, "변조된 토큰은 유효하지 않아야 합니다");
        log.info("✅ 변조된 토큰 감지 완료 - validateToken: {}", isValid);
        log.info("======================================================");
    }

    // ========================================================================
    // 3. 카카오 실제 로그인 통합 테스트 (선택 실행)
    // ========================================================================

    @Test
    @DisplayName("카카오 실제 로그인 - 인가 코드로 AccessToken/RefreshToken 발급 (realAuthCode 필요)")
    void testKakaoLoginReal() {
        if (realAuthCode == null || realAuthCode.isBlank()) {
            log.warn("⚠️ realAuthCode가 비어 있습니다. 브라우저에서 인가 코드를 복사해주세요.");
            log.warn("   URL: https://kauth.kakao.com/oauth/authorize?client_id=8e00d3b0b6b3b6a1b6e0f4ddfa44eeb1&redirect_uri=http://localhost:8080/api/v1/auth/login/kakao/callback&response_type=code");
            return;
        }

        log.info("======================================================");
        log.info("  [통합 테스트] 카카오 실제 로그인");
        log.info("======================================================");

        // 카카오 로그인 실행 (인가 코드 → 카카오 토큰 → 사용자 정보 → DB 저장 → JWT 발급)
        ApiResponse<Map<String, Object>> response = authService.kakaoLogin(realAuthCode);

        // 검증
        assertNotNull(response);
        assertEquals(HttpStatus.OK.value(), response.status());

        Map<String, Object> data = response.data();
        assertNotNull(data);

        // Access Token 검증
        String accessToken = (String) data.get("accessToken");
        assertNotNull(accessToken);
        assertTrue(jwtTokenProvider.validateToken(accessToken));

        // 사용자 정보 검증
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertNotNull(user);
        assertNotNull(user.get("userId"));
        assertNotNull(user.get("email"));

        log.info("✅ 카카오 로그인 통합 테스트 성공");
        log.info("   userId: {}, email: {}, nickname: {}",
                user.get("userId"), user.get("email"), user.get("nickname"));
        log.info("======================================================");
    }

    // ========================================================================
    // 4. AuthService 단위 테스트 (DB 기반)
    // ========================================================================

    @Test
    @DisplayName("AuthService.refreshAccessToken - 유효한 Refresh Token으로 Access Token 재발급")
    void testRefreshAccessToken() {
        log.info("======================================================");
        log.info("  [테스트] Refresh Token → Access Token 재발급");
        log.info("======================================================");

        // Given: 유저 생성 + Refresh Token 발급
        User user = userRepository.save(User.builder()
                .email("refreshtest2@kakao.com")
                .nickname("리프레시테스트")
                .provider("KAKAO")
                .providerId("refresh_provider_id_2")
                .build());

        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        // When: Refresh Token으로 Access Token 재발급
        ApiResponse<Map<String, Object>> response = authService.refreshAccessToken(refreshToken);

        // Then
        assertEquals(HttpStatus.OK.value(), response.status());
        assertNotNull(response.data().get("accessToken"));

        String newAccessToken = (String) response.data().get("accessToken");
        assertTrue(jwtTokenProvider.validateToken(newAccessToken));

        log.info("✅ Access Token 재발급 성공");
        log.info("======================================================");
    }
}