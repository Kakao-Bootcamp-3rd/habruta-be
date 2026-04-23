package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeRanking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 챌린지 랭킹 스냅샷 Repository
 * - 커버링 인덱스(idx_rankings_view) 활용: user JOIN 없이 스냅샷 필드로 응답 조립
 */
public interface ChallengeRankingRepository extends JpaRepository<ChallengeRanking, Long> {

    // ===== Read API 용 (페이지네이션) =====

    /**
     * 챌린지 랭킹 목록 조회 - 페이지네이션 (GET /rankings 용)
     */
    Page<ChallengeRanking> findByChallengeIdOrderByRankNoAsc(Long challengeId, Pageable pageable);

    /**
     * 특정 챌린지에서 유저의 랭킹 조회 (내 순위 조회용)
     */
    @Query("""
            SELECT r FROM ChallengeRanking r
            WHERE r.challenge.id = :challengeId AND r.user.id = :userId
            """)
    Optional<ChallengeRanking> findByChallengeIdAndUserId(
            @Param("challengeId") Long challengeId,
            @Param("userId") Long userId
    );

    /**
     * 여러 챌린지에서 유저의 랭킹 bulk 조회 (히스토리 맵 조립용)
     */
    @Query("""
            SELECT r FROM ChallengeRanking r
            WHERE r.challenge.id IN :challengeIds AND r.user.id = :userId
            """)
    List<ChallengeRanking> findByChallengeIdInAndUserId(
            @Param("challengeIds") List<Long> challengeIds,
            @Param("userId") Long userId
    );

    /**
     * 챌린지 참여자 수
     */
    long countByChallengeId(Long challengeId);

    // ===== 스케줄러 용 =====

    /**
     * 챌린지 랭킹 전체 목록 (스케줄러 배치용)
     */
    List<ChallengeRanking> findByChallengeIdOrderByRankNoAsc(Long challengeId);

    /** 배치 중복 방지 확인 */
    boolean existsByChallengeId(Long challengeId);

    @Modifying
    @Query("DELETE FROM ChallengeRanking r WHERE r.challenge.id = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);
}