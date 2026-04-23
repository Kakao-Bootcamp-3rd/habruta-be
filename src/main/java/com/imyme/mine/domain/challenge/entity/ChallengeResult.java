package com.imyme.mine.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 챌린지 도전 AI 채점 결과
 * <p>
 * {@link ChallengeAttempt}와 1:1 식별 관계 ({@code @MapsId}).
 * 무거운 JSONB 데이터를 분리하여 {@code challenge_attempts}의 쓰기 성능을 보호.
 * </p>
 *
 * <p>트랜잭션 경계 (AI 워커):
 * <ol>
 *   <li>challenge_results INSERT (score, feedback_json)</li>
 *   <li>challenge_attempts UPDATE (status = COMPLETED, finished_at)</li>
 *   <li>Redis ZADD 랭킹 반영 — DB 커밋 성공 후 실행</li>
 * </ol>
 * </p>
 */
@Entity
@Table(name = "challenge_results")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeResult {

    /** PK이자 FK (식별 관계, attempt_id = challenge_results.id) */
    @Id
    @Column(name = "attempt_id")
    private Long attemptId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "attempt_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChallengeAttempt attempt;

    /** AI 평가 점수 (0~100) */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** AI 생성 상세 피드백 원본 (JSONB) */
    @Column(name = "feedback_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String feedbackJson;

    /** 분석에 사용된 프롬프트/LLM 모델 버전 (A/B 테스트 및 모델 교체 추적) */
    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}