package com.imyme.mine.domain.challenge.scheduler;

import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.challenge.service.ChallengeGateService;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

/**
 * 챌린지 파이프라인 배치 스케줄러
 *
 * <pre>
 * 00:05  — 내일 챌린지 레코드 생성 (SCHEDULED)
 * 22:00  — 오늘 챌린지 OPEN
 * 22:10  — 오늘 챌린지 CLOSED (제출 마감)
 * 22:12  — 오늘 챌린지 ANALYZING + UPLOADED 제출 일괄 MQ 발행
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeScheduler {

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final KeywordRepository keywordRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationBroadcastService notificationBroadcastService;
    private final ChallengeGateService challengeGateService;

    private static final Random RANDOM = new Random();
    // CLOSED 시점에 아직 upload-complete를 보내지 않은 PENDING 수 (조기 게이트 종료 판단용)
    private static final String REDIS_PENDING_UPLOADS_KEY = "challenge:%d:pending_uploads";
    // CLOSED 시점의 active_stt_count 확인용
    private static final String REDIS_ACTIVE_STT_KEY = "challenge:%d:active_stt_count";
    private static final Duration PENDING_UPLOADS_TTL = Duration.ofHours(2);

    // -------------------------------------------------------------------------
    // 00:05 — 내일 챌린지 생성
    // -------------------------------------------------------------------------

    /**
     * 내일 챌린지 레코드 생성
     *
     * <p>활성 키워드 중 랜덤 선택하여 내일 날짜의 챌린지를 SCHEDULED 상태로 생성.
     * {@code challenge_date} UNIQUE 제약으로 중복 실행 시 INSERT 건너뜀(멱등성).
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void createTomorrowChallenge() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        if (challengeRepository.existsByChallengeDate(tomorrow)) {
            log.info("[Challenge] 내일 챌린지 이미 존재 - date={}", tomorrow);
            return;
        }

        List<Keyword> activeKeywords = keywordRepository.findAllWithCategoryByIsActive(true);
        if (activeKeywords.isEmpty()) {
            log.warn("[Challenge] 활성 키워드 없음 — 챌린지 생성 건너뜀");
            return;
        }

        Keyword keyword = activeKeywords.get(RANDOM.nextInt(activeKeywords.size()));

        Challenge challenge = Challenge.builder()
                .keyword(keyword)
                .keywordText(keyword.getName())
                .challengeDate(tomorrow)
                .startAt(LocalDateTime.of(tomorrow, LocalTime.of(22, 0)))
                .endAt(LocalDateTime.of(tomorrow, LocalTime.of(22, 9, 59)))
                .status(ChallengeStatus.SCHEDULED)
                .build();

        challengeRepository.save(challenge);
        log.info("[Challenge] 내일 챌린지 생성 완료 - date={}, keyword={}", tomorrow, keyword.getName());
    }

    // -------------------------------------------------------------------------
    // 22:00 — 챌린지 OPEN
    // -------------------------------------------------------------------------

    /**
     * 오늘 SCHEDULED 챌린지를 OPEN으로 전환 + 전체 유저 CHALLENGE_OPEN 알림 발송
     *
     * <p>오늘 날짜 + SCHEDULED 상태인 챌린지가 없으면 로그만 기록 후 종료.
     * DB 커밋 후 알림을 발송하여 챌린지 상태가 확정된 이후에만 알림이 나가도록 보장.
     */
    @Scheduled(cron = "0 0 22 * * *")
    @Transactional
    public void openChallenge() {
        challengeRepository
                .findByChallengeDateAndStatus(LocalDate.now(), ChallengeStatus.SCHEDULED)
                .ifPresentOrElse(
                        challenge -> {
                            challenge.open();

                            Long challengeId = challenge.getId();
                            String keywordText = challenge.getKeywordText();

                            log.info("[Challenge] OPEN 전환 완료 - challengeId={}", challengeId);

                            // 커밋 후 전체 활성 유저에게 CHALLENGE_OPEN 알림 배치 발송
                            TransactionSynchronizationManager.registerSynchronization(
                                    new TransactionSynchronization() {
                                        @Override
                                        public void afterCommit() {
                                            notificationBroadcastService.broadcastToAllActive(
                                                    NotificationType.CHALLENGE_OPEN,
                                                    "오늘의 챌린지가 시작됐어요!",
                                                    "\"" + keywordText + "\" 주제로 지금 도전해보세요.",
                                                    challengeId,
                                                    "CHALLENGE"
                                            );
                                            log.info("[Challenge] CHALLENGE_OPEN 브로드캐스트 제출 - challengeId={}", challengeId);
                                        }
                                    }
                            );
                        },
                        () -> log.warn("[Challenge] OPEN 대상 챌린지 없음 - date={}", LocalDate.now())
                );
    }

    // -------------------------------------------------------------------------
    // 22:10 — 챌린지 CLOSED
    // -------------------------------------------------------------------------

    /**
     * OPEN 챌린지를 CLOSED로 전환 (신규 제출 차단)
     *
     * <p>OPEN 상태 챌린지가 없으면 로그만 기록 후 종료.
     */
    @Scheduled(cron = "0 10 22 * * *")
    @Transactional
    public void closeChallenge() {
        challengeRepository
                .findFirstByStatusOrderByIdDesc(ChallengeStatus.OPEN)
                .ifPresentOrElse(
                        challenge -> {
                            challenge.close();

                            Long challengeId = challenge.getId();

                            // CLOSED 시점 PENDING 수 스냅샷: 아직 upload-complete를 보내지 않은 참여자 수
                            // → CLOSED 이후 upload-complete 수신마다 DECR, 0이 되면 조기 게이트 종료
                            int pendingCount = challengeAttemptRepository.countByChallengeIdAndStatus(
                                    challengeId, ChallengeAttemptStatus.PENDING);

                            log.info("[Challenge] CLOSED 전환 완료 - challengeId={}, pending_uploads={}",
                                    challengeId, pendingCount);

                            TransactionSynchronizationManager.registerSynchronization(
                                    new TransactionSynchronization() {
                                        @Override
                                        public void afterCommit() {
                                            if (pendingCount > 0) {
                                                // PENDING이 남아있으면 카운트 저장 (upload-complete가 DECR)
                                                stringRedisTemplate.opsForValue().set(
                                                        String.format(REDIS_PENDING_UPLOADS_KEY, challengeId),
                                                        String.valueOf(pendingCount),
                                                        PENDING_UPLOADS_TTL
                                                );
                                            } else {
                                                // PENDING == 0: 모두 이미 upload-complete 완료
                                                // active_stt_count가 0이면 즉시 게이트 종료
                                                String countStr = stringRedisTemplate.opsForValue()
                                                        .get(String.format(REDIS_ACTIVE_STT_KEY, challengeId));
                                                long activeStt = countStr != null ? Long.parseLong(countStr) : 0;
                                                log.info("[Challenge] CLOSED 시점 pending_uploads=0, active_stt_count={}",
                                                        activeStt);
                                                if (activeStt <= 0) {
                                                    log.info("[Challenge] 모든 STT 완료 → 즉시 게이트 종료: challengeId={}", challengeId);
                                                    challengeGateService.closeGate(challengeId);
                                                }
                                                // activeStt > 0이면 마지막 STT 응답 시 ChallengeAsyncService가 처리
                                            }
                                        }
                                    }
                            );
                        },
                        () -> log.warn("[Challenge] CLOSED 대상 챌린지 없음")
                );
    }

    // -------------------------------------------------------------------------
    // 22:11:30 — 분석 게이트 타임아웃 (CLOSED + 90초)
    // -------------------------------------------------------------------------

    /**
     * 분석 게이트 타임아웃 — CLOSED 후 90초 경과 시 강제 게이트 종료
     *
     * <p>설계 근거: max(10MB) / min_upload_speed(1Mbps) × 1.1 ≈ 88초 → 90초
     * 1Mbps 미만 환경은 서비스 이용 불가로 간주 (한국 LTE 1%ile 기준)
     *
     * <p>대부분의 경우 이 스케줄러가 실행되기 전에 조기 게이트 종료가 완료된다.
     * ANALYZING 이미 전환 시 {@link ChallengeGateService#closeGate}가 멱등으로 skip.
     *
     * <p>Eager STT로 upload-complete 시점에 STT MQ를 발행하므로,
     * 이 스케줄러는 STT를 직접 발행하지 않고 게이트만 닫는다.
     */
    @Scheduled(cron = "30 11 22 * * *")
    public void startAnalyzing() {
        challengeRepository
                .findFirstByStatusOrderByIdDesc(ChallengeStatus.CLOSED)
                .ifPresentOrElse(
                        challenge -> {
                            log.info("[Challenge] 90s 타임아웃 → 게이트 종료 시도: challengeId={}", challenge.getId());
                            challengeGateService.closeGate(challenge.getId());
                        },
                        () -> log.info("[Challenge] 게이트 이미 종료됨 또는 대상 없음 (조기 종료 완료)")
                );
    }
}