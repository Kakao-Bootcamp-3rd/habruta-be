package com.imyme.mine.domain.card.repository;

import com.imyme.mine.domain.card.entity.Card;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByIdAndUserId(Long id, Long userId);

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        JOIN FETCH c.user
        WHERE c.id = :cardId
        AND c.user.id = :userId
        AND c.deletedAt IS NULL
        """)
    Optional<Card> findByIdAndUserIdWithRelations(
        @Param("cardId") Long cardId,
        @Param("userId") Long userId
    );

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        WHERE c.user.id = :userId
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:keywordIds IS NULL OR c.keyword.id IN :keywordIds)
        AND (:excludeGhost = false OR c.attemptCount > 0)
        ORDER BY c.createdAt DESC, c.id DESC
        """)
    List<Card> findCardsRecentFirst(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("keywordIds") List<Long> keywordIds,
        @Param("excludeGhost") boolean excludeGhost,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        WHERE c.user.id = :userId
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:keywordIds IS NULL OR c.keyword.id IN :keywordIds)
        AND (:excludeGhost = false OR c.attemptCount > 0)
        AND (c.createdAt < :cursorCreatedAt
             OR (c.createdAt = :cursorCreatedAt AND c.id < :cursorId))
        ORDER BY c.createdAt DESC, c.id DESC
        """)
    List<Card> findCardsRecentAfterCursor(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("keywordIds") List<Long> keywordIds,
        @Param("excludeGhost") boolean excludeGhost,
        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
        @Param("cursorId") Long cursorId,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        WHERE c.user.id = :userId
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:keywordIds IS NULL OR c.keyword.id IN :keywordIds)
        AND (:excludeGhost = false OR c.attemptCount > 0)
        ORDER BY c.createdAt ASC, c.id ASC
        """)
    List<Card> findCardsOldestFirst(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("keywordIds") List<Long> keywordIds,
        @Param("excludeGhost") boolean excludeGhost,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Card c
        JOIN FETCH c.category
        JOIN FETCH c.keyword
        WHERE c.user.id = :userId
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:keywordIds IS NULL OR c.keyword.id IN :keywordIds)
        AND (:excludeGhost = false OR c.attemptCount > 0)
        AND (c.createdAt > :cursorCreatedAt
             OR (c.createdAt = :cursorCreatedAt AND c.id > :cursorId))
        ORDER BY c.createdAt ASC, c.id ASC
        """)
    List<Card> findCardsOldestAfterCursor(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("keywordIds") List<Long> keywordIds,
        @Param("excludeGhost") boolean excludeGhost,
        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
        @Param("cursorId") Long cursorId,
        Pageable pageable
    );

    // -------------------------------------------------------------------------
    // 배치용 — @SQLRestriction("deleted_at IS NULL") 우회를 위해 네이티브 쿼리 사용
    // -------------------------------------------------------------------------

    /** Soft Delete된 카드 Hard Delete 대상 ID 조회 (30일 경과, 청크 단위) */
    @Query(value = """
        SELECT id FROM cards
        WHERE deleted_at IS NOT NULL
          AND deleted_at < :threshold
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findDeletedCardIdsForHardDelete(
        @Param("threshold") LocalDateTime threshold,
        @Param("limit") int limit
    );

    /** 카드 DB Hard Delete (cascade: card_attempts, card_feedbacks) */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cards WHERE id IN :ids", nativeQuery = true)
    int hardDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 유령 카드 Soft Delete (배치용: attempt_count=0, 7일 경과)
     * @SQLDelete(UPDATE cards SET deleted_at = NOW())를 bulk로 수행
     * attempt_count=0 → audio 없음 → S3 삭제 불필요
     */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE cards
        SET deleted_at = NOW()
        WHERE attempt_count = 0
          AND created_at < :threshold
          AND deleted_at IS NULL
        """, nativeQuery = true)
    int softDeleteGhostCards(@Param("threshold") LocalDateTime threshold);
}
