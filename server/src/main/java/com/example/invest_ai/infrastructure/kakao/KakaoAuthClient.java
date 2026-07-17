package com.example.invest_ai.infrastructure.kakao;

import com.example.invest_ai.global.error.CustomException;
import com.example.invest_ai.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 카카오 OAuth2 인증 서버 HTTP 클라이언트
 *
 * - exchangeToken(code): 인가 코드 → 카카오 Access Token
 * - getUserInfo(token): 카카오 Access Token → 사용자 정보
 */
@Slf4j
@Component
public class KakaoAuthClient {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUrl;
    private final String userInfoUrl;

    public KakaoAuthClient(
            @Value("${kakao.oauth.client-id}") String clientId,
            @Value("${kakao.oauth.client-secret}") String clientSecret,
            @Value("${kakao.oauth.redirect-uri}") String redirectUri,
            @Value("${kakao.oauth.token-url}") String tokenUrl,
            @Value("${kakao.oauth.user-info-url}") String userInfoUrl
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
        this.webClient = WebClient.builder().build();
    }

    /**
     * 인가 코드 → 카카오 Access Token 교환
     * POST https://kauth.kakao.com/oauth/token
     */
    public KakaoTokenResponse exchangeToken(String code) {
        log.info("→ 카카오 토큰 교환 요청");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri(tokenUrl)
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .bodyValue(formData)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String accessToken = (String) response.get("access_token");
            String refreshToken = (String) response.get("refresh_token");
            Integer expiresIn = (Integer) response.get("expires_in");

            log.info("← 카카오 Access Token 발급 성공");
            return new KakaoTokenResponse(accessToken, refreshToken, expiresIn);
        } catch (Exception e) {
            log.error("❌ 카카오 토큰 교환 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    /**
     * 카카오 Access Token → 사용자 정보 조회
     * GET https://kapi.kakao.com/v2/user/me
     */
    public KakaoUserInfoResponse getUserInfo(String kakaoAccessToken) {
        log.info("→ 카카오 사용자 정보 요청");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri(userInfoUrl)
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> kakaoAccount = (Map<String, Object>) response.get("kakao_account");
            String email = (String) kakaoAccount.get("email");

            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            String nickname = (String) profile.get("nickname");

            String providerId = String.valueOf(response.get("id"));

            log.info("← 카카오 사용자 정보 수신: email={}, nickname={}", email, nickname);
            return new KakaoUserInfoResponse(providerId, email, nickname);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ 카카오 사용자 정보 요청 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    public record KakaoTokenResponse(String accessToken, String refreshToken, Integer expiresIn) {}
    public record KakaoUserInfoResponse(String providerId, String email, String nickname) {}
}