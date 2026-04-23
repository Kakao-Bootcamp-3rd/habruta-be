package com.imyme.mine.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 기기 정보 엔티티
 * - PWA 및 웹 푸시 환경에 최적화
 * - User와 느슨한 관계 (유저가 없어도 기기는 존재 가능)
 */
@Entity
@Table(name = "devices")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE devices SET deleted_at = NOW() WHERE id = ?")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기기 UUID (고유 식별자)
    @Column(name = "device_uuid", nullable = false, length = 36)
    private String deviceUuid;

    // FCM 토큰 (푸시 알림용)
    @Column(name = "fcm_token", columnDefinition = "TEXT")
    private String fcmToken;

    // 브라우저 타입 (iOS, Android, Web)
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 20)
    @ColumnDefault("'CHROME'")
    @Builder.Default
    private AgentType agentType = AgentType.CHROME;

    // 플랫폼 (Mobile Web, Desktop Web)
    @Enumerated(EnumType.STRING)
    @Column(name = "platform_type", nullable = false, length = 20)
    @ColumnDefault("'MOBILE_WEB'")
    @Builder.Default
    private PlatformType platformType = PlatformType.MOBILE_WEB;

    // PWA 모드 여부 (홈 화면 추가 여부)
    @Column(name = "is_standalone", nullable = false)
    @Builder.Default
    private boolean isStandalone = false;

    // 마지막 로그인 유저 (리텐션 마케팅용, Nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_user_id") // DB FK 설정에서 ON DELETE SET NULL 적용 필요
    private User lastUser;

    // 푸시 수신 동의 여부
    @Column(name = "is_push_enabled", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean isPushEnabled = true;

    // 마지막 활동 일시
    @Column(name = "last_active_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Builder.Default
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    // 생성 일시
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 삭제 일시 (Soft Delete)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 로그인 시 유저 및 활동 시간 갱신
    public void login(User user) {
        this.lastUser = user;
        this.lastActiveAt = LocalDateTime.now();
    }

    // FCM 토큰 갱신
    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
        this.lastActiveAt = LocalDateTime.now();
    }

    // 푸시 설정 변경
    public void changePushStatus(boolean isEnabled) {
        this.isPushEnabled = isEnabled;
        this.lastActiveAt = LocalDateTime.now();
    }

    // PWA 모드 변경
    public void updateStandaloneMode(boolean isStandalone) {
        this.isStandalone = isStandalone;
    }
}
