package com.imyme.mine.domain.notification.entity;

import com.imyme.mine.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 유저별 알림 수신 설정 (1유저 1행)
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // LEVEL_UP
    @Column(name = "allow_growth", nullable = false)
    @Builder.Default
    private Boolean allowGrowth = true;

    // SOLO_RESULT
    @Column(name = "allow_solo_result", nullable = false)
    @Builder.Default
    private Boolean allowSoloResult = true;

    // PVP_RESULT
    @Column(name = "allow_pvp_result", nullable = false)
    @Builder.Default
    private Boolean allowPvpResult = true;

    // CHALLENGE_OPEN, CHALLENGE_PERSONAL_RESULT, CHALLENGE_OVERALL_RESULT
    @Column(name = "allow_challenge", nullable = false)
    @Builder.Default
    private Boolean allowChallenge = true;

    // SYSTEM
    @Column(name = "allow_system", nullable = false)
    @Builder.Default
    private Boolean allowSystem = true;

    // 미접속 리마인드 푸시 수신 여부 (알림함 DB 저장 없이 푸시 전송만 제어)
    @Column(name = "allow_inactivity", nullable = false)
    @Builder.Default
    private Boolean allowInactivity = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ===== 비즈니스 메서드 =====

    public static NotificationPreference defaultFor(User user) {
        return NotificationPreference.builder()
            .user(user)
            .build();
    }

    public void update(
        Boolean allowGrowth,
        Boolean allowSoloResult,
        Boolean allowPvpResult,
        Boolean allowChallenge,
        Boolean allowSystem,
        Boolean allowInactivity
    ) {
        this.allowGrowth = allowGrowth;
        this.allowSoloResult = allowSoloResult;
        this.allowPvpResult = allowPvpResult;
        this.allowChallenge = allowChallenge;
        this.allowSystem = allowSystem;
        this.allowInactivity = allowInactivity;
    }
}
