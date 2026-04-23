package com.imyme.mine.domain.challenge.controller;

import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRankingRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeResultRepository;
import com.imyme.mine.domain.challenge.scheduler.ChallengeScheduler;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 챌린지 파이프라인 수동 트리거 API (dev/release 전용)
 *
 * <p>밤 10시 고정 스케줄러를 수동으로 단계별 실행할 수 있어 테스트 편의성 제공.
 * {@code /setup}은 하루에 몇 번이든 호출 가능 — 기존 데이터 초기화 후 SCHEDULED로 재설정.
 *
 * <pre>
 * POST /admin/challenge/setup    — 오늘 날짜 챌린지 생성 (기존 있으면 초기화 후 재사용)
 * POST /admin/challenge/open     — SCHEDULED → OPEN
 * POST /admin/challenge/close    — OPEN → CLOSED
 * POST /admin/challenge/analyze  — CLOSED → ANALYZING + STT MQ 발행
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/admin/challenge")
@Profile({"dev", "release"})
@RequiredArgsConstructor
public class ChallengeAdminController {

    private final ChallengeScheduler challengeScheduler;
    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final ChallengeRankingRepository challengeRankingRepository;
    private final ChallengeResultRepository challengeResultRepository;
    private final KeywordRepository keywordRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private static final Random RANDOM = new Random();

    /**
     * 오늘 날짜 챌린지 초기화 + SCHEDULED 상태 준비
     *
     * <ul>
     *   <li>오늘 챌린지 없음 → 새로 생성</li>
     *   <li>오늘 챌린지 있음 → ChallengeResult / ChallengeRanking / ChallengeAttempt 전부 삭제,
     *       Redis 정리 후 Challenge 상태를 SCHEDULED로 리셋</li>
     * </ul>
     * 하루에 몇 번이든 호출해서 파이프라인을 처음부터 다시 테스트 가능.
     */
    @PostMapping("/setup")
    @Transactional
    public ResponseEntity<String> setup() {
        LocalDate today = LocalDate.now();

        Challenge existing = challengeRepository.findByChallengeDate(today).orElse(null);

        if (existing != null) {
            Long challengeId = existing.getId();

            // 1. 자식 데이터 삭제 (FK 순서 주의: result → ranking → attempt)
            challengeResultRepository.deleteByChallengeId(challengeId);
            challengeRankingRepository.deleteByChallengeId(challengeId);
            challengeAttemptRepository.deleteByChallengeId(challengeId);

            // 2. Redis 정리
            cleanupRedis(challengeId);

            // 3. Challenge 상태 초기화 (startAt/endAt도 원래 시간으로 복구)
            challengeRepository.resetToScheduled(
                    challengeId,
                    today.atTime(22, 0),
                    today.atTime(22, 9, 59)
            );

            log.info("[Admin] 챌린지 초기화 완료: id={}, date={}", challengeId, today);
            return ResponseEntity.ok("챌린지 초기화 완료 (SCHEDULED 리셋): " + existing.getKeywordText());
        }

        // 새로 생성
        List<Keyword> keywords = keywordRepository.findAllWithCategoryByIsActive(true);
        if (keywords.isEmpty()) {
            return ResponseEntity.badRequest().body("활성 키워드 없음");
        }

        Keyword keyword = keywords.get(RANDOM.nextInt(keywords.size()));
        LocalDateTime now = LocalDateTime.now();

        Challenge challenge = Challenge.builder()
                .keyword(keyword)
                .keywordText(keyword.getName())
                .challengeDate(today)
                .startAt(now)
                .endAt(now.plusMinutes(10))
                .status(ChallengeStatus.SCHEDULED)
                .build();

        challengeRepository.save(challenge);
        log.info("[Admin] 오늘 챌린지 생성: date={}, keyword={}", today, keyword.getName());
        return ResponseEntity.ok("챌린지 생성 완료 - 키워드: " + keyword.getName());
    }

    /**
     * SCHEDULED → OPEN
     *
     * <p>테스트 편의를 위해 startAt/endAt을 호출 시점 기준으로 갱신.
     * (스케줄러 생성분은 22:09:59 고정이라 프론트 녹음 타이머가 즉시 만료됨)
     */
    @PostMapping("/open")
    @Transactional
    public ResponseEntity<String> open() {
        LocalDateTime now = LocalDateTime.now();
        challengeRepository
                .findByChallengeDateAndStatus(LocalDate.now(), ChallengeStatus.SCHEDULED)
                .ifPresentOrElse(
                        challenge -> challenge.openForTest(now, now.plusMinutes(10)),
                        () -> log.warn("[Admin] OPEN 대상 챌린지 없음")
                );
        return ResponseEntity.ok("open 완료");
    }

    /** OPEN → CLOSED */
    @PostMapping("/close")
    public ResponseEntity<String> close() {
        challengeScheduler.closeChallenge();
        return ResponseEntity.ok("close 완료");
    }

    /** CLOSED → ANALYZING + STT MQ 발행 */
    @PostMapping("/analyze")
    public ResponseEntity<String> analyze() {
        challengeScheduler.startAnalyzing();
        return ResponseEntity.ok("analyze 완료");
    }

    private void cleanupRedis(Long challengeId) {
        Set<String> pairsKeys = stringRedisTemplate.keys("pairs:job:" + challengeId + ":*");
        if (pairsKeys != null && !pairsKeys.isEmpty()) {
            stringRedisTemplate.delete(pairsKeys);
        }
        stringRedisTemplate.delete("challenge:" + challengeId + ":active_stt_count");
        stringRedisTemplate.delete("challenge:" + challengeId + ":submitted_count");
        stringRedisTemplate.delete("challenge:" + challengeId + ":gate_closed");
        stringRedisTemplate.delete("challenge:" + challengeId + ":pending_uploads");
        stringRedisTemplate.delete("challenge:" + challengeId + ":participants");
        stringRedisTemplate.delete("challenge:" + challengeId + ":final_ranking");
        stringRedisTemplate.delete("challenge:" + challengeId + ":feedbacks");
    }
}