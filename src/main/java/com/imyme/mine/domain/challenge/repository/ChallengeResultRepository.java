package com.imyme.mine.domain.challenge.repository;

import com.imyme.mine.domain.challenge.entity.ChallengeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChallengeResultRepository extends JpaRepository<ChallengeResult, Long> {

    /** attemptId = PK(@MapsId)이므로 findById와 동일하나 명시적 사용 */
    Optional<ChallengeResult> findByAttemptId(Long attemptId);

    @Modifying
    @Query("DELETE FROM ChallengeResult r WHERE r.attempt.id IN (SELECT a.id FROM ChallengeAttempt a WHERE a.challenge.id = :challengeId)")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);
}