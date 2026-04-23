package com.imyme.mine.domain.notification.repository;

import com.imyme.mine.domain.notification.entity.Notification;
import com.imyme.mine.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 Repository
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 알림 목록 조회 (커서 없음, 최신순)
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.user.id = :userId
        AND (:isRead IS NULL OR n.isRead = :isRead)
        AND (:type IS NULL OR n.type = :type)
        ORDER BY n.createdAt DESC, n.id DESC
        """)
    List<Notification> findNotificationsFirst(
        @Param("userId") Long userId,
        @Param("isRead") Boolean isRead,
        @Param("type") NotificationType type,
        Pageable pageable
    );

    /**
     * 알림 목록 조회 (커서 이후, 최신순)
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.user.id = :userId
        AND (:isRead IS NULL OR n.isRead = :isRead)
        AND (:type IS NULL OR n.type = :type)
        AND (n.createdAt < :cursorCreatedAt
             OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId))
        ORDER BY n.createdAt DESC, n.id DESC
        """)
    List<Notification> findNotificationsAfterCursor(
        @Param("userId") Long userId,
        @Param("isRead") Boolean isRead,
        @Param("type") NotificationType type,
        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
        @Param("cursorId") Long cursorId,
        Pageable pageable
    );

    /**
     * 읽지 않은 알림 일괄 읽음 처리 (단일 UPDATE)
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);

    /**
     * 읽지 않은 알림 개수 조회
     */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * 읽은 알림 Hard Delete (배치용: 30일 경과)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.createdAt < :threshold")
    int deleteOldReadNotifications(@Param("threshold") LocalDateTime threshold);
}