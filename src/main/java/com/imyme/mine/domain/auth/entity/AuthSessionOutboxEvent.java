package com.imyme.mine.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "auth_session_outbox_events",
    indexes = {
        @Index(name = "idx_auth_session_outbox_status_next_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_auth_session_outbox_created", columnList = "created_at")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSessionOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AuthSessionOutboxEventType eventType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_uuid", length = 36)
    private String deviceUuid;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AuthSessionOutboxStatus status = AuthSessionOutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "next_retry_at", nullable = false)
    @Builder.Default
    private LocalDateTime nextRetryAt = LocalDateTime.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextRetryAt == null) {
            nextRetryAt = now;
        }
        if (status == null) {
            status = AuthSessionOutboxStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markDone() {
        this.status = AuthSessionOutboxStatus.DONE;
        this.processedAt = LocalDateTime.now();
        this.lastError = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage, int maxRetries) {
        this.retryCount++;
        this.lastError = errorMessage;
        this.status = retryCount >= maxRetries ? AuthSessionOutboxStatus.DEAD : AuthSessionOutboxStatus.FAILED;
        this.nextRetryAt = LocalDateTime.now().plusSeconds(nextBackoffSeconds());
        this.updatedAt = LocalDateTime.now();
    }

    private long nextBackoffSeconds() {
        return Math.min(300L, (long) Math.pow(2, Math.min(retryCount, 8)));
    }
}
