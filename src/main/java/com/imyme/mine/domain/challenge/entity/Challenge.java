package com.imyme.mine.domain.challenge.entity;

import com.imyme.mine.domain.keyword.entity.Keyword;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일일 챌린지 마스터 테이블
 * <p>
 * 매일 22:00 ~ 22:09:59에 진행되는 챌린지.
 * 진행 중 실시간 참여자 수는 Redis에서 관리하며,
 * 챌린지 종료 후 {@code participant_count}에 스냅샷 반영.
 * </p>
 */
@Entity
@Table(name = "challenges", indexes = {
        @Index(name = "idx_challenges_completed_list", columnList = "status, challenge_date DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    /** 키워드명 스냅샷 (키워드 삭제/변경 대비 조인 최소화) */
    @Column(name = "keyword_text", nullable = false, length = 50)
    private String keywordText;

    /** 챌린지 진행 일자 (하루 1개만 존재, UNIQUE) */
    @Column(name = "challenge_date", nullable = false, unique = true)
    private LocalDate challengeDate;

    /** 챌린지 시작 시각 (22:00:00) */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** 챌린지 종료 시각 (22:09:59) */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChallengeStatus status;

    /** 1위(명예의 전당) 달성자의 제출 (탈퇴 시 NULL, ON DELETE SET NULL) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "best_submission_id")
    private ChallengeAttempt bestSubmission;

    /**
     * 1~3위 랭킹 및 전체 통계 요약 (JSONB 스냅샷)
     * <pre>
     * {
     *   "top3": [{ "rank": 1, "user_id": 123, "nickname": "철수", "score": 95, "attempt_id": 456 }],
     *   "summary": { "total_participants": 80, "average_score": 67.5, "completion_rate": 0.95 }
     * }
     * </pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary_json", columnDefinition = "jsonb")
    private String resultSummaryJson;

    /**
     * 최종 참여자 수
     * 진행 중에는 Redis에서 카운트, 종료 시 DB에 반영
     */
    @Column(name = "participant_count", nullable = false)
    @Builder.Default
    private Integer participantCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ChallengeStatus.SCHEDULED;
        }
    }

    // ===== 비즈니스 메서드 =====

    /** 챌린지 시작 (22:00) */
    public void open() {
        this.status = ChallengeStatus.OPEN;
    }

    /** 테스트용: startAt/endAt을 현재 시각 기준으로 갱신 후 OPEN */
    public void openForTest(LocalDateTime startAt, LocalDateTime endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = ChallengeStatus.OPEN;
    }

    /** 제출 마감 (22:10) */
    public void close() {
        this.status = ChallengeStatus.CLOSED;
    }

    /** AI 채점 시작 (22:12) */
    public void startAnalyzing() {
        this.status = ChallengeStatus.ANALYZING;
    }

    /**
     * 채점 및 랭킹 완료 처리
     *
     * @param bestSubmissionId  1위 제출 ID
     * @param resultSummaryJson 랭킹 및 통계 JSON 스냅샷
     * @param participantCount  Redis에서 집계한 최종 참여자 수
     */
    public void complete(ChallengeAttempt bestSubmission, String resultSummaryJson, int participantCount) {
        this.bestSubmission = bestSubmission;
        this.resultSummaryJson = resultSummaryJson;
        this.participantCount = participantCount;
        this.status = ChallengeStatus.COMPLETED;
    }

    /** 진행 중 여부 */
    public boolean isOpen() {
        return this.status == ChallengeStatus.OPEN;
    }

    /** 제출 가능 여부 (OPEN 상태에서만 가능) */
    public boolean isAcceptingSubmissions() {
        return this.status == ChallengeStatus.OPEN;
    }
}