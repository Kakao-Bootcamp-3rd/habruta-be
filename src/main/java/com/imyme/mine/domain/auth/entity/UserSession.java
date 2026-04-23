package com.imyme.mine.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 사용자 세션 엔티티
 * - Refresh Token 관리 및 RT Rotation 지원
 * - 기기별 세션 관리 (1 User : N Sessions / 1 Device : N Sessions)
 */
@Entity
@Table(
    name = "user_sessions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_sessions_refresh_token",
            columnNames = {"refresh_token"}
        )
    },
    indexes = {
        @Index(name = "idx_sessions_user", columnList = "user_id, last_used_at DESC"),
        @Index(name = "idx_sessions_device", columnList = "device_id"),
        @Index(name = "idx_sessions_expires", columnList = "expires_at")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 FK (유저 삭제 시 세션도 삭제)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    // 기기 FK (기기 삭제 시 세션도 삭제)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Device device;

    // Refresh Token (SHA-256 해시값으로 저장)
    @Column(name = "refresh_token", nullable = false, length = 500)
    private String refreshToken;

    // Refresh Token 만료 시간
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 생성 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    // 마지막 Refresh 일시 (명세서의 last_used_at 반영)
    @Column(name = "last_used_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    // Refresh Token 갱신 (RT Rotation)
    public void rotateRefreshToken(String newRefreshToken, LocalDateTime newExpiresAt) {
        this.refreshToken = newRefreshToken;
        this.expiresAt = newExpiresAt;
        this.lastUsedAt = LocalDateTime.now();
    }

    // Refresh Token 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
