package com.imyme.mine.domain.challenge.service;

import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * 챌린지 분석 게이트 서비스
 *
 * <p>"게이트"란 더 이상 upload-complete를 받지 않겠다는 선언으로,
 * CLOSED → ANALYZING 상태 전환과 동일하다.
 *
 * <p>게이트 종료 트리거:
 * <ol>
 *   <li><b>조기 종료</b>: CLOSED 시점의 PENDING 수가 0이거나,
 *       CLOSED 이후 마지막 upload-complete 수신 시 {@code pending_uploads == 0}</li>
 *   <li><b>타임아웃</b>: 22:11:30 (CLOSED + 90초) — max(10MB) / min_speed(1Mbps) × 1.1 ≈ 88s 근거</li>
 * </ol>
 *
 * <p>멱등 설계: {@code transitionToAnalyzing}이 WHERE status=CLOSED 조건을 걸어
 * 중복 호출 시 0을 반환하므로 안전하게 여러 경로에서 호출 가능하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeGateService {

    private static final String REDIS_ACTIVE_STT_KEY = "challenge:%d:active_stt_count";
    private static final String REDIS_GATE_CLOSED_KEY = "challenge:%d:gate_closed";
    private static final Duration GATE_TTL = Duration.ofHours(4);

    private final ChallengeRepository challengeRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RankingInitService rankingInitService;
    private final ChallengeParticipantSseService challengeParticipantSseService;

    /**
     * 게이트 종료: CLOSED → ANALYZING 원자 전환 + gate_closed 플래그 설정 + ranking 조기 트리거
     *
     * <p>이미 ANALYZING 상태라면 조용히 skip (멱등).
     *
     * @param challengeId 대상 챌린지 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeGate(Long challengeId) {
        int updated = challengeRepository.transitionToAnalyzing(challengeId);
        if (updated == 0) {
            log.info("[Challenge Gate] skip — 이미 ANALYZING: challengeId={}", challengeId);
            return;
        }

        log.info("[Challenge Gate] ANALYZING 전환 완료: challengeId={}", challengeId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // gate_closed 플래그 설정 → ChallengeAsyncService가 이 플래그를 보고 ranking 트리거
                stringRedisTemplate.opsForValue().set(
                        String.format(REDIS_GATE_CLOSED_KEY, challengeId),
                        "1",
                        GATE_TTL
                );

                // active_stt_count 확인: 이미 모두 완료됐으면 즉시 ranking 시작
                String countStr = stringRedisTemplate.opsForValue()
                        .get(String.format(REDIS_ACTIVE_STT_KEY, challengeId));
                long activeStt = countStr != null ? Long.parseLong(countStr) : 0;

                log.info("[Challenge Gate] gate_closed 설정: challengeId={}, active_stt_count={}",
                        challengeId, activeStt);

                // SSE 참여자 수 스트림 종료 (CLOSED → 이후 참여 불가)
                challengeParticipantSseService.closeAll(challengeId);

                if (activeStt <= 0) {
                    log.info("[Challenge Gate] STT 모두 완료 → 즉시 ranking 시작: challengeId={}", challengeId);
                    rankingInitService.initRanking(challengeId);
                }
                // activeStt > 0이면 마지막 STT 응답 수신 시 ChallengeAsyncService가 ranking 트리거
            }
        });
    }
}
