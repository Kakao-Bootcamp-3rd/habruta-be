package com.imyme.mine.domain.pvp.entity;

import com.imyme.mine.domain.auth.entity.User;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * PvP 피드백
 */
@Entity
@Table(name = "pvp_feedbacks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_feedbacks_room_user", columnNames = {"room_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_pvp_feedbacks_room", columnList = "room_id, deleted_at")
        })
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE pvp_feedbacks SET deleted_at = NOW() WHERE id = ?")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class PvpFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private PvpRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "score")
    private Integer score;

    @Type(JsonBinaryType.class)
    @Column(name = "pvp_feedback_json", nullable = false, columnDefinition = "jsonb")
    private Object pvpFeedbackJson;

    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ===== 비즈니스 메서드 =====

    /**
     * Soft Delete
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}