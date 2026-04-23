package com.imyme.mine.domain.challenge.entity;

import com.imyme.mine.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 챌린지 도전 기록
 * <p>
 * 유저가 일일 챌린지에 제출한 녹음 파일 및 분석 상태를 관리.
 * 10분 내 대량 INSERT가 발생하므로 인덱스를 최소화하고 MQ 기반 비동기 처리를 전제.
 * </p>
 *
 * <p>부분 인덱스 (Flyway에서 생성):
 * {@code CREATE INDEX idx_attempts_challenge_completed
 *   ON challenge_attempts (challenge_id, submitted_at DESC)
 *   WHERE status = 'COMPLETED';}
 * </p>
 */
@Entity
@Table(
        name = "challenge_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attempts_challenge_user",
                columnNames = {"challenge_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ChallengeAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    /** 탈퇴 유저의 제출 기록 보존을 위해 nullable (ON DELETE SET NULL) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** 녹음 파일 S3 Object Key */
    @Column(name = "audio_key", length = 500)
    private String audioKey;

    /** 녹음 길이 (초) — upload-complete 시 클라이언트가 전달 */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** STT 변환 텍스트 — Branch 3 배치에서 채움 */
    @Column(name = "stt_text", columnDefinition = "TEXT")
    private String sttText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChallengeAttemptStatus status;

    /** 도전 생성(시작) 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 파일 제출 완료 시각 (동점 시 타이브레이커) */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** AI 분석 및 채점 완료 시각 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ChallengeAttemptStatus.PENDING;
        }
    }

    // ===== 비즈니스 메서드 =====

    /**
     * PENDING 재사용: 새 object key 재발급 시 이전 업로드 무효화
     */
    public void refreshUploadReservation(String audioKey) {
        this.audioKey = audioKey;
        this.submittedAt = null;
        this.durationSeconds = null;
        this.sttText = null;
        this.status = ChallengeAttemptStatus.PENDING;
    }

    /**
     * 업로드 완료 확정: PENDING → UPLOADED
     */
    public void markUploadCompleted(String audioKey, Integer durationSeconds) {
        this.audioKey = audioKey;
        this.durationSeconds = durationSeconds;
        this.submittedAt = LocalDateTime.now();
        this.status = ChallengeAttemptStatus.UPLOADED;
    }

    /** AI 워커가 작업을 꺼내갔을 때 (중복 처리 방지) */
    public void startProcessing() {
        this.status = ChallengeAttemptStatus.PROCESSING;
    }

    /** STT 텍스트 저장 (PROCESSING 상태 유지 — 랭킹 완료 전 중간 단계) */
    public void saveSttText(String sttText) {
        this.sttText = sttText;
    }

    /** 랭킹 및 피드백 확정 완료 (PROCESSING → COMPLETED) */
    public void markCompleted() {
        this.status = ChallengeAttemptStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    /** @deprecated STT와 피드백이 분리된 새 플로우에서는 saveSttText() + markCompleted() 사용 */
    @Deprecated
    public void complete(String sttText) {
        this.sttText = sttText;
        this.status = ChallengeAttemptStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    /** 분석 실패 (배치 재시도 대상) */
    public void fail() {
        this.status = ChallengeAttemptStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
    }
}