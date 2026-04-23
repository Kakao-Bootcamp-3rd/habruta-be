package com.imyme.mine.domain.pvp.repository;

import com.imyme.mine.domain.pvp.entity.PvpHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PvP 대결 이력 Repository
 */
public interface PvpHistoryRepository extends JpaRepository<PvpHistory, Long> {

    /**
     * 내 기록 조회 (Cursor 페이징)
     * 4.8 API용
     */
    @Query("""
            SELECT h FROM PvpHistory h
            WHERE h.user.id = :userId
            AND (:includeHidden = true OR h.isHidden = false)
            AND (CAST(:categoryId AS long) IS NULL OR h.category.id = :categoryId)
            AND (CAST(:keywordId AS long) IS NULL OR h.keyword.id = :keywordId)
            AND (CAST(:cursor AS timestamp) IS NULL OR h.finishedAt < :cursor OR (h.finishedAt = :cursor AND h.id < :lastId))
            ORDER BY h.finishedAt DESC, h.id DESC
            """)
    List<PvpHistory> findMyHistories(
            @Param("userId") Long userId,
            @Param("includeHidden") boolean includeHidden,
            @Param("categoryId") Long categoryId,
            @Param("keywordId") Long keywordId,
            @Param("cursor") LocalDateTime cursor,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    /**
     * 내 기록 조회 (점수순 정렬)
     */
    @Query("""
            SELECT h FROM PvpHistory h
            WHERE h.user.id = :userId
            AND (:includeHidden = true OR h.isHidden = false)
            AND (CAST(:categoryId AS long) IS NULL OR h.category.id = :categoryId)
            AND (CAST(:keywordId AS long) IS NULL OR h.keyword.id = :keywordId)
            AND (CAST(:cursorScore AS int) IS NULL OR h.score < :cursorScore OR (h.score = :cursorScore AND h.id < :lastId))
            ORDER BY h.score DESC, h.id DESC
            """)
    List<PvpHistory> findMyHistoriesByScore(
            @Param("userId") Long userId,
            @Param("includeHidden") boolean includeHidden,
            @Param("categoryId") Long categoryId,
            @Param("keywordId") Long keywordId,
            @Param("cursorScore") Integer cursorScore,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    /**
     * 특정 방의 특정 유저 이력 조회
     */
    Optional<PvpHistory> findByRoomIdAndUserId(Long roomId, Long userId);

    /**
     * 여러 방의 상대방 점수 일괄 조회 (N+1 방지)
     *
     * @param roomIds 방 ID 목록
     * @return roomId를 키로 하는 PvpHistory 목록
     */
    @Query("""
            SELECT h FROM PvpHistory h
            WHERE h.room.id IN :roomIds
            """)
    List<PvpHistory> findByRoomIdIn(@Param("roomIds") List<Long> roomIds);

    /**
     * 승률/평균 점수 통계 조회
     */
    @Query("""
            SELECT COUNT(h), AVG(h.score), SUM(CASE WHEN h.isWinner = true THEN 1 ELSE 0 END)
            FROM PvpHistory h
            WHERE h.user.id = :userId
            """)
    Object[] getStatsByUserId(@Param("userId") Long userId);
}