package com.example.invest_ai.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원 테이블 (database.md §① users)
 *
 * OAuth(구글/카카오) 로그인 정보를 담는 기준 테이블.
 * JWT 발급 시 user_id가 토큰에 포함되어 모든 API에서 사용자를 식별.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_provider", columnNames = {"provider", "provider_id"})
        })
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public User(String email, String nickname, String provider, String providerId) {
        this.email = email;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.createdAt = LocalDateTime.now();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}