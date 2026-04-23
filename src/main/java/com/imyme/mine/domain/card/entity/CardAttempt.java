package com.imyme.mine.domain.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 카드 학습 시도 엔티티
 * - 사용자의 녹음 및 학습 이력 관리
 * - 오디오 파일은 S3 Object Key로 저장
 */
@Entity
@Table(
    name = "card_attempts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_attempts_card_no",
            columnNames = {"card_id", "attempt_no"}
        )
    },
    indexes = {
        @Index(name = "idx_attempts_card_created", columnList = "card_id, created_at DESC"),
        @Index(name = "idx_attempts_status", columnList = "status, created_at ASC")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CardAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 카드 (카드 삭제 시 시도 이력도 Cascade 삭제)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Card card;

    @Column(name = "attempt_no", nullable = false)
    private Short attemptNo;

    // S3 Object Key
    @Column(name = "audio_key", length = 500)
    private String audioKey;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "stt_text", columnDefinition = "TEXT")
    private String sttText;

    // 초기 상태는 PENDING (배치 정리 대상)
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private AttemptStatus status = AttemptStatus.PENDING;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // --- 비즈니스 로직 ---

    // 1. 업로드 URL 발급 단계 (PENDING 상태 유지)
    public void reserveAudioKey(String audioKey) {
        this.audioKey = audioKey;
    }

    // 2. 업로드 완료 처리 (PENDING -> UPLOADED)
    // 클라이언트가 업로드 후 완료 API 호출 시 실행
    public void markUploaded(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
        this.status = AttemptStatus.UPLOADED;
        this.submittedAt = LocalDateTime.now();
    }

    // 3. 분석 시작 (UPLOADED -> PROCESSING)
    public void startProcessing() {
        this.status = AttemptStatus.PROCESSING;
    }

    // 4. 분석 완료 (PROCESSING -> COMPLETED)
    // STT 텍스트만 먼저 저장하는 경우를 위해 오버로딩 지원
    public void recordSttResult(String sttText) {
        this.sttText = sttText;
    }

    public void complete() {
        this.status = AttemptStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    // 편의 메서드: 텍스트 저장과 완료를 한 번에
    public void complete(String sttText) {
        this.sttText = sttText;
        this.complete();
    }

    // 5. 실패 처리
    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = AttemptStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
    }

    // 6. 만료 처리 (배치 등에서 사용)
    public void expire() {
        this.status = AttemptStatus.EXPIRED;
        this.finishedAt = LocalDateTime.now();
    }
}
