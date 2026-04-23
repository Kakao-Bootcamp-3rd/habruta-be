package com.imyme.mine.domain.pvp.entity;

import com.imyme.mine.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * PvP 제출
 */
@Entity
@Table(name = "pvp_submissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_submissions_room_user", columnNames = {"room_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_submissions_room", columnList = "room_id"),
                @Index(name = "idx_submissions_status", columnList = "status, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class PvpSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private PvpRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "stt_text", columnDefinition = "TEXT")
    private String sttText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PvpSubmissionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = PvpSubmissionStatus.PENDING;
        }
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 오디오 URL 설정 (S3 업로드 완료)
     */
    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    /**
     * 제출 완료 (분석 대기)
     */
    public void submit(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
        this.status = PvpSubmissionStatus.UPLOADED;
        this.submittedAt = LocalDateTime.now();
    }

    /**
     * 분석 시작
     */
    public void startProcessing() {
        this.status = PvpSubmissionStatus.PROCESSING;
    }

    /**
     * STT 텍스트 저장
     */
    public void saveSttText(String sttText) {
        this.sttText = sttText;
    }

    /**
     * 분석 완료
     */
    public void complete() {
        this.status = PvpSubmissionStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 분석 실패
     */
    public void fail() {
        this.status = PvpSubmissionStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
    }
}