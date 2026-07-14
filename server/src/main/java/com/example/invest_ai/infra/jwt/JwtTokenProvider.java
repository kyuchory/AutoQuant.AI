package com.example.invest_ai.infra.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret-key}") String secretKey,
            @Value("${jwt.access-token-expiration:3600}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:1209600}") long refreshTokenExpiration
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiration = accessTokenExpiration * 1000; // 초 → 밀리초
        this.refreshTokenExpiration = refreshTokenExpiration * 1000;
    }

    /**
     * Access Token 발급 (api.md §2.1)
     * payload: userId (sub), role
     */
    public String createAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Refresh Token 발급 (api.md §2.2)
     * 더 긴 만료 시간, userId를 subject로 사용
     */
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰에서 userId 추출
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 토큰 유효성 검증 (api.md §1.4 E4010)
     * @return 유효하면 true, 만료/변조되었으면 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("변조된 JWT 토큰: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JWT Claims 파싱
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}