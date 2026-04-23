package com.imyme.mine.domain.card.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 학습 시도 AI 피드백 엔티티
 * - CardAttempt와 1:1 식별 관계 (@MapsId 사용)
 * - 부모(Attempt)가 존재해야만 생성 가능
 */
@Entity
@Table(name = "card_feedbacks")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CardFeedback {

    // PK이자 FK (식별 관계)
    @Id
    @Column(name = "attempt_id")
    private Long attemptId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // CardAttempt의 ID를 내 PK로 사용
    @JoinColumn(name = "attempt_id")
    @OnDelete(action = OnDeleteAction.CASCADE) // DB FK Cascade
    private CardAttempt attempt;

    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    @Column(name = "level", nullable = false)
    private Short level;

    // AI 피드백 원본 (JSONB)
    @Column(name = "feedback_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String feedbackJson;

    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;

    // 생성 일시 (명세서 반영하여 추가)
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
