package com.example.invest_ai.infra.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 카카오 OAuth2 인증 서버와의 HTTP 통신을 담당하는 클라이언트
 *
 * application.yml §8 Kakao OAuth2 Configuration 참조
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * 인가 코드를 이용해 카카오 Access Token 발급
     * POST https://kauth.kakao.com/oauth/token
     */
    public KakaoTokenResponse getAccessToken(String authorizationCode) {
        log.info("→ 카카오 토큰 발급 요청 (code: {}...)", authorizationCode.substring(0, Math.min(10, authorizationCode.length())));

        // application/x-www-form-urlencoded 폼 데이터 구성
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);
        formData.add("code", authorizationCode);

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
        String tokenType = (String) response.get("token_type");
        Integer expiresIn = (Integer) response.get("expires_in");

        log.info("← 카카오 Access Token 발급 성공 (expires_in: {}초)", expiresIn);
        return new KakaoTokenResponse(accessToken, refreshToken, tokenType, expiresIn);
    }

    /**
     * 카카오 Access Token으로 사용자 정보 조회
     * GET https://kapi.kakao.com/v2/user/me
     */
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        log.info("→ 카카오 사용자 정보 요청");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.get()
                .uri(userInfoUrl)
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // kakao_account 파싱
        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) response.get("kakao_account");
        String email = (String) kakaoAccount.get("email");

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        String nickname = (String) profile.get("nickname");

        String providerId = String.valueOf(response.get("id"));

        log.info("← 카카오 사용자 정보 수신 완료 (email: {}, nickname: {})", email, nickname);
        return new KakaoUserInfo(providerId, email, nickname);
    }

    /**
     * 카카오 토큰 응답 DTO
     */
    public record KakaoTokenResponse(String accessToken, String refreshToken, String tokenType, Integer expiresIn) {}

    /**
     * 카카오 사용자 정보 DTO
     */
    public record KakaoUserInfo(String providerId, String email, String nickname) {}
}