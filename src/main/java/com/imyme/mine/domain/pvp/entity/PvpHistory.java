package com.imyme.mine.domain.pvp.entity;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.keyword.entity.Keyword;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * PvP 대결 이력
 */
@Entity
@Table(name = "pvp_histories", indexes = {
        @Index(name = "idx_pvp_history_user", columnList = "user_id, is_hidden, finished_at"),
        @Index(name = "idx_pvp_history_stats", columnList = "user_id, is_winner, score"),
        @Index(name = "idx_pvp_history_keyword", columnList = "keyword_id, user_id, finished_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class PvpHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private PvpRoom room;

    @Column(name = "room_name", nullable = false, length = 30)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private PvpRole role;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "is_winner", nullable = false)
    private Boolean isWinner;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private Boolean isHidden = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_user_id")
    private User opponentUser;

    @Column(name = "opponent_nickname", nullable = false, length = 20)
    private String opponentNickname;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "category_name", nullable = false, length = 20)
    private String categoryName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id")
    private Keyword keyword;

    @Column(name = "keyword_name", nullable = false, length = 50)
    private String keywordName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 기록 숨기기/보이기
     */
    public void updateHiddenStatus(boolean hidden) {
        this.isHidden = hidden;
    }
}