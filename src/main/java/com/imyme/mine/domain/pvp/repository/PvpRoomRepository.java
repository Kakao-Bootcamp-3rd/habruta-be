package com.imyme.mine.domain.pvp.repository;

import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PvP 방 Repository
 */
public interface PvpRoomRepository extends JpaRepository<PvpRoom, Long> {

    /**
     * 방 목록 조회 (카테고리 필터, 첫 페이지)
     */
    @Query("""
            SELECT r FROM PvpRoom r
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.hostUser
            LEFT JOIN FETCH r.guestUser
            WHERE r.category.id = :categoryId
            AND r.status = :status
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<PvpRoom> findRoomsByCategoryAndStatus(
            @Param("categoryId") Long categoryId,
            @Param("status") PvpRoomStatus status,
            Pageable pageable
    );

    /**
     * 방 목록 조회 (카테고리 필터, 커서 페이징)
     */
    @Query("""
            SELECT r FROM PvpRoom r
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.hostUser
            LEFT JOIN FETCH r.guestUser
            WHERE r.category.id = :categoryId
            AND r.status = :status
            AND (r.createdAt < :cursor OR (r.createdAt = :cursor AND r.id < :lastId))
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<PvpRoom> findRoomsByCategoryAndStatusWithCursor(
            @Param("categoryId") Long categoryId,
            @Param("status") PvpRoomStatus status,
            @Param("cursor") LocalDateTime cursor,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    /**
     * 전체 방 목록 조회 (첫 페이지)
     */
    @Query("""
            SELECT r FROM PvpRoom r
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.hostUser
            LEFT JOIN FETCH r.guestUser
            WHERE r.status = :status
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<PvpRoom> findRoomsByStatus(
            @Param("status") PvpRoomStatus status,
            Pageable pageable
    );

    /**
     * 전체 방 목록 조회 (커서 페이징)
     */
    @Query("""
            SELECT r FROM PvpRoom r
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.hostUser
            LEFT JOIN FETCH r.guestUser
            WHERE r.status = :status
            AND (r.createdAt < :cursor OR (r.createdAt = :cursor AND r.id < :lastId))
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<PvpRoom> findRoomsByStatusWithCursor(
            @Param("status") PvpRoomStatus status,
            @Param("cursor") LocalDateTime cursor,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    /**
     * 호스트가 생성한 OPEN 상태 방 조회 (중복 방지용)
     */
    Optional<PvpRoom> findByHostUserIdAndStatus(Long hostUserId, PvpRoomStatus status);

    /**
     * 방 조회 with fetch join (N+1 방지)
     */
    @Query("""
            SELECT r FROM PvpRoom r
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.keyword
            LEFT JOIN FETCH r.hostUser
            LEFT JOIN FETCH r.guestUser
            WHERE r.id = :roomId
            """)
    Optional<PvpRoom> findByIdWithDetails(@Param("roomId") Long roomId);

    /**
     * 비관적 락 + fetch join (동시성 방어용)
     * - Feedback Request 중복 발행 방지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM PvpRoom r
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.keyword
            LEFT JOIN FETCH r.hostUser
            LEFT JOIN FETCH r.guestUser
            WHERE r.id = :roomId
            """)
    Optional<PvpRoom> findByIdWithDetailsForUpdate(@Param("roomId") Long roomId);

    /**
     * 유령 방 일괄 EXPIRED 처리 (배치용)
     * - OPEN / MATCHED / THINKING 상태에서 1시간 초과 방을 EXPIRED로 전환
     * - @Version 낙관적 잠금 미적용 (배치 맥락: 해당 방은 이미 이탈된 상태)
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE PvpRoom r
            SET r.status = :expired
            WHERE r.status IN :statuses
            AND r.createdAt < :threshold
            """)
    int expireGhostRooms(
            @Param("expired") PvpRoomStatus expired,
            @Param("statuses") List<PvpRoomStatus> statuses,
            @Param("threshold") LocalDateTime threshold
    );
}