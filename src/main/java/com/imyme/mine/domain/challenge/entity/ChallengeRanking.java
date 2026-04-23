package com.imyme.mine.domain.challenge.entity;

import com.imyme.mine.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 챌린지 최종 랭킹 스냅샷 (명예의 전당)
 * <p>
 * 챌린지 종료 후 Redis Sorted Set → RDBMS로 단 1회 Bulk Insert.
 * 조회 성능을 위해 유저 정보를 스냅샷으로 보관 (JOIN 불필요).
 * </p>
 *
 * <p>커버링 인덱스 (Flyway에서 생성):
 * {@code CREATE UNIQUE INDEX uk_rankings_challenge_user
 *   ON challenge_rankings (challenge_id, user_id);}
 * ※ user_id 가 NULL인 경우 PostgreSQL은 NULL ≠ NULL로 처리하므로
 *    탈퇴 유저 다수의 레코드가 유니크 위반 없이 공존 가능.
 *
 * {@code CREATE INDEX idx_rankings_view
 *   ON challenge_rankings (challenge_id, rank_no ASC)
 *   INCLUDE (user_nickname, user_profile_image_url, score);}
 * → Index-Only Scan으로 Heap 접근 없이 랭킹 목록 반환.
 * </p>
 */
@Entity
@Table(name = "challenge_rankings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ChallengeRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Challenge challenge;

    /** 탈퇴 유저의 과거 랭킹 기록 보존을 위해 nullable (ON DELETE SET NULL) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.NO_ACTION) // SET NULL → Flyway FK로 처리
    private User user;

    /** [스냅샷] 챌린지 종료 당시 닉네임 */
    @Column(name = "user_nickname", nullable = false, length = 20)
    private String userNickname;

    /** [스냅샷] 챌린지 종료 당시 프로필 이미지 URL */
    @Column(name = "user_profile_image_url", length = 500)
    private String userProfileImageUrl;

    /** 최종 등수 (1, 2, 3...) */
    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    /** 랭킹 산출 기준 점수 */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** 랭킹 근거 제출 기록 (상세 보기 연결, 탈퇴/삭제 시 NULL) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    @OnDelete(action = OnDeleteAction.NO_ACTION) // SET NULL → Flyway FK로 처리
    private ChallengeAttempt attempt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}