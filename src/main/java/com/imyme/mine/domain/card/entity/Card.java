package com.imyme.mine.domain.card.entity;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.keyword.entity.Keyword;
import jakarta.persistence.*;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import java.time.LocalDateTime;

/**
 * 학습 카드 엔티티
 * - 사용자가 생성한 학습 단위
 * - Soft Delete 적용
 */
@Entity
@Table(
    name = "cards",
    indexes = {
        // Partial Index 조건(WHERE deleted_at IS NULL)은 Flyway DDL에서 처리됨
        @Index(name = "idx_cards_user_category", columnList = "user_id, category_id, keyword_id"),
        @Index(name = "idx_cards_user_created", columnList = "user_id, created_at DESC")
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE cards SET deleted_at = NOW() WHERE id = ?")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소유 유저 (유저 삭제 시 카드도 Cascade 삭제)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    // 카테고리 (참조 무결성 유지 - Restrict)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // 키워드 (참조 무결성 유지 - Restrict)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    @Column(name = "title", nullable = false, length = 20)
    private String title;

    // 최고 달성 레벨 (1~5)
    @Column(name = "best_level", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Short bestLevel = 0;

    // 총 학습 시도 횟수
    @Column(name = "attempt_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Short attemptCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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

    // --- 비즈니스 로직 ---

    public void updateTitle(String newTitle) {
        this.title = newTitle;
    }

    // 학습 완료 시 통계 업데이트
    public void completeAttempt(short achievedLevel) {
        this.attemptCount++;
        if (achievedLevel > this.bestLevel) {
            this.bestLevel = achievedLevel;
        }
    }
}
