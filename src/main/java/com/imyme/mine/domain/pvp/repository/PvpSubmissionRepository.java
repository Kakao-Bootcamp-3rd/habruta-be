package com.imyme.mine.domain.pvp.repository;

import com.imyme.mine.domain.pvp.entity.PvpSubmission;
import com.imyme.mine.domain.pvp.entity.PvpSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PvP 제출 Repository
 */
public interface PvpSubmissionRepository extends JpaRepository<PvpSubmission, Long> {

    /**
     * 제출 조회 (room_id, user_id)
     */
    Optional<PvpSubmission> findByRoomIdAndUserId(Long roomId, Long userId);

    /**
     * 방의 모든 제출 조회
     */
    @Query("""
            SELECT s FROM PvpSubmission s
            LEFT JOIN FETCH s.user
            WHERE s.room.id = :roomId
            """)
    List<PvpSubmission> findByRoomIdWithUser(@Param("roomId") Long roomId);

    /**
     * 특정 상태의 제출 목록 조회 (배치 처리용)
     */
    List<PvpSubmission> findByStatusOrderByCreatedAtAsc(PvpSubmissionStatus status);

    /**
     * 방의 완료된 제출 개수 확인
     */
    @Query("""
            SELECT COUNT(s) FROM PvpSubmission s
            WHERE s.room.id = :roomId
            AND s.status = :status
            """)
    long countByRoomIdAndStatus(@Param("roomId") Long roomId, @Param("status") PvpSubmissionStatus status);

    /**
     * 중복 제출 확인 (UNIQUE 제약 체크용)
     */
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    /**
     * PENDING 상태 지연 제출 Hard Delete (배치용: 1시간 초과)
     * PENDING 상태는 audio_url 미발급 → S3 삭제 불필요
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PvpSubmission s WHERE s.status = :status AND s.createdAt < :threshold")
    int deleteStaleSubmissions(
            @Param("status") PvpSubmissionStatus status,
            @Param("threshold") LocalDateTime threshold
    );
}