package com.imyme.mine.domain.pvp.repository;

import com.imyme.mine.domain.pvp.entity.PvpFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * PvP 피드백 Repository
 */
public interface PvpFeedbackRepository extends JpaRepository<PvpFeedback, Long> {

    /**
     * 피드백 조회 (room_id, user_id)
     */
    @Query("""
            SELECT f FROM PvpFeedback f
            WHERE f.room.id = :roomId
            AND f.user.id = :userId
            AND f.deletedAt IS NULL
            """)
    Optional<PvpFeedback> findByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /**
     * 방의 모든 피드백 조회 (Soft Delete 제외)
     */
    @Query("""
            SELECT f FROM PvpFeedback f
            LEFT JOIN FETCH f.user
            WHERE f.room.id = :roomId
            AND f.deletedAt IS NULL
            """)
    List<PvpFeedback> findByRoomIdWithUser(@Param("roomId") Long roomId);

    /**
     * 방의 완료된 피드백 개수 확인
     */
    @Query("""
            SELECT COUNT(f) FROM PvpFeedback f
            WHERE f.room.id = :roomId
            AND f.deletedAt IS NULL
            """)
    long countByRoomId(@Param("roomId") Long roomId);

    /**
     * 중복 피드백 확인
     */
    @Query("""
            SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
            FROM PvpFeedback f
            WHERE f.room.id = :roomId
            AND f.user.id = :userId
            AND f.deletedAt IS NULL
            """)
    boolean existsByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);
}