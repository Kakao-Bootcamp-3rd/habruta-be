package com.imyme.mine.domain.pvp.entity;

import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * PvP 대결 방
 */
@Entity
@Table(name = "pvp_rooms", indexes = {
        @Index(name = "idx_rooms_category", columnList = "category_id, created_at"),
        @Index(name = "idx_rooms_status", columnList = "id, status, finished_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class PvpRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id")
    private Keyword keyword;

    @Column(name = "room_name", nullable = false, length = 30)
    private String roomName;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PvpRoomStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_user_id")
    private User hostUser;

    @Column(name = "host_nickname", nullable = false, length = 20)
    private String hostNickname;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_user_id")
    private User guestUser;

    @Column(name = "guest_nickname", length = 20)
    private String guestNickname;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_user_id")
    private User winnerUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "feedback_requested_at")
    private LocalDateTime feedbackRequestedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = PvpRoomStatus.OPEN;
        }
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 게스트 입장
     */
    public void joinGuest(User guest, String guestNickname) {
        this.guestUser = guest;
        this.guestNickname = guestNickname;
        this.status = PvpRoomStatus.MATCHED;
        this.matchedAt = LocalDateTime.now();
    }

    /**
     * 키워드 확정 및 THINKING 전환
     */
    public void startThinking(Keyword keyword) {
        this.keyword = keyword;
        this.status = PvpRoomStatus.THINKING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * RECORDING 전환
     */
    public void startRecording() {
        this.status = PvpRoomStatus.RECORDING;
    }

    /**
     * PROCESSING 전환
     */
    public void startProcessing() {
        this.status = PvpRoomStatus.PROCESSING;
    }

    /**
     * 게임 종료 (승자 확정)
     */
    public void finish(User winner) {
        this.winnerUser = winner;
        this.status = PvpRoomStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * Feedback Request 발행 시각 기록 (중복 발행 방지)
     */
    public void markFeedbackRequested() {
        this.feedbackRequestedAt = LocalDateTime.now();
    }

    /**
     * Feedback Request 이미 발행되었는지 확인
     */
    public boolean isFeedbackRequested() {
        return this.feedbackRequestedAt != null;
    }

    /**
     * 방 취소
     */
    public void cancel() {
        this.status = PvpRoomStatus.CANCELED;
    }

    /**
     * 게스트 퇴장 (매칭 취소)
     */
    public void removeGuest() {
        this.guestUser = null;
        this.guestNickname = null;
        this.status = PvpRoomStatus.OPEN;
        this.matchedAt = null;
        // THINKING에서 나간 경우 키워드/시작 시간 초기화
        this.keyword = null;
        this.startedAt = null;
    }

    /**
     * 유령 방 만료 처리 (배치 전용)
     */
    public void expire() {
        this.status = PvpRoomStatus.EXPIRED;
    }

    /**
     * 호스트인지 확인
     */
    public boolean isHost(Long userId) {
        return hostUser != null && hostUser.getId().equals(userId);
    }

    /**
     * 게스트인지 확인
     */
    public boolean isGuest(Long userId) {
        return guestUser != null && guestUser.getId().equals(userId);
    }

    /**
     * 참여자인지 확인
     */
    public boolean isParticipant(Long userId) {
        return isHost(userId) || isGuest(userId);
    }
}