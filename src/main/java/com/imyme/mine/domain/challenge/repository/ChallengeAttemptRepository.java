package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChallengeAttemptRepository extends JpaRepository<ChallengeAttempt, Long> {

    // ===== Read API 용 =====

    /**
     * 특정 챌린지에 대한 유저의 참여 기록 조회
     */
    @Query("""
            SELECT a FROM ChallengeAttempt a
            WHERE a.challenge.id = :challengeId AND a.user.id = :userId
            """)
    Optional<ChallengeAttempt> findByChallengeIdAndUserId(
            @Param("challengeId") Long challengeId,
            @Param("userId") Long userId
    );

    /**
     * 여러 챌린지에 대한 유저의 참여 기록 bulk 조회 (히스토리 맵 조립용)
     */
    @Query("""
            SELECT a FROM ChallengeAttempt a
            WHERE a.challenge.id IN :challengeIds AND a.user.id = :userId
            """)
    List<ChallengeAttempt> findByChallengeIdInAndUserId(
            @Param("challengeIds") List<Long> challengeIds,
            @Param("userId") Long userId
    );

    // ===== MQ Consumer 용 =====

    /**
     * MQ Consumer용 단건 조회 — user fetch join 포함
     * (알림 발송을 위해 userId 필요, userId는 MQ payload에 없으므로 조인으로 확보)
     */
    @Query("""
            SELECT a FROM ChallengeAttempt a
            LEFT JOIN FETCH a.user
            WHERE a.id = :id AND a.challenge.id = :challengeId
            """)
    Optional<ChallengeAttempt> findByIdAndChallengeIdWithUser(
            @Param("id") Long id,
            @Param("challengeId") Long challengeId
    );

    // ===== 스케줄러 용 =====

    /** upload-complete 소유권 검증 포함 단건 조회 */
    Optional<ChallengeAttempt> findByIdAndChallengeIdAndUserId(Long id, Long challengeId, Long userId);

    /** 중복 참여 확인 */
    boolean existsByChallengeIdAndUserId(Long challengeId, Long userId);

    /** 특정 상태의 참여 수 (CLOSED 시점 pending_uploads 스냅샷용) */
    int countByChallengeIdAndStatus(Long challengeId, ChallengeAttemptStatus status);

    /** 특정 상태의 제출 목록 조회 */
    List<ChallengeAttempt> findByChallengeIdAndStatus(Long challengeId, ChallengeAttemptStatus status);

    /** 분석 큐 일괄 발행용 — UPLOADED 상태, 제출 시각 순 정렬 (22:12 스케줄러) */
    List<ChallengeAttempt> findByChallengeIdAndStatusOrderBySubmittedAtAsc(
            Long challengeId, ChallengeAttemptStatus status
    );

    /** 제출 완료된 참여자 수 (PENDING 제외) — SSE 참여자 수 브로드캐스트용 */
    @Query("""
            SELECT COUNT(a) FROM ChallengeAttempt a
            WHERE a.challenge.id = :challengeId
              AND a.status <> com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus.PENDING
            """)
    int countSubmittedByChallengeId(@Param("challengeId") Long challengeId);

    @Modifying
    @Query("DELETE FROM ChallengeAttempt a WHERE a.challenge.id = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);
}