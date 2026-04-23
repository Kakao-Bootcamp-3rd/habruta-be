package com.imyme.mine.domain.card.repository;

import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.CardAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardAttemptRepository extends JpaRepository<CardAttempt, Long> {

    @Query("SELECT ca FROM CardAttempt ca WHERE ca.card.id = :cardId ORDER BY ca.attemptNo DESC")
    List<CardAttempt> findByCardIdOrderByAttemptNoDesc(@Param("cardId") Long cardId);

    @Query("SELECT MAX(ca.attemptNo) FROM CardAttempt ca WHERE ca.card.id = :cardId")
    Short findMaxAttemptNoByCardId(@Param("cardId") Long cardId);

    long countByCardId(Long cardId);

    /**
     * 특정 상태를 제외한 카드 시도 개수 조회
     * - MAX_ATTEMPTS 체크 시 FAILED/EXPIRED는 제외해야 함
     */
    long countByCardIdAndStatusNotIn(Long cardId, List<AttemptStatus> excludedStatuses);

    /**
     * 특정 상태이고 생성 시간이 특정 시간 이전인 시도 조회
     * - 스케줄러에서 만료 처리용
     */
    List<CardAttempt> findByStatusAndCreatedAtBefore(AttemptStatus status, LocalDateTime createdAt);

    /**
     * CardAttempt를 Card 및 User와 함께 조회 (Lazy Loading 방지)
     * - SoloFeedbackSaveService에서 사용
     * - Virtual Thread에서 실행되므로 fetch join 필수
     */
    @Query("""
        SELECT ca FROM CardAttempt ca
        JOIN FETCH ca.card c
        JOIN FETCH c.user
        WHERE ca.id = :attemptId
        """)
    Optional<CardAttempt> findByIdWithCardAndUser(@Param("attemptId") Long attemptId);

    /**
     * 시도 상태만 조회 (SSE Race Condition 방어용)
     * - SSE 구독 시점에 이미 COMPLETED/FAILED 상태면 즉시 이벤트 전송 후 종료
     */
    @Query("SELECT ca.status FROM CardAttempt ca WHERE ca.id = :id")
    Optional<AttemptStatus> findStatusById(@Param("id") Long id);

    /**
     * 카드 ID 목록에 해당하는 S3 오디오 키 조회 (배치용 Hard Delete 전처리)
     */
    @Query("SELECT ca.audioKey FROM CardAttempt ca WHERE ca.card.id IN :cardIds AND ca.audioKey IS NOT NULL")
    List<String> findAudioKeysByCardIds(@Param("cardIds") List<Long> cardIds);
}
