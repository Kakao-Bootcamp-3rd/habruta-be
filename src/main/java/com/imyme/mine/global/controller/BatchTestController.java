package com.imyme.mine.global.controller;

import com.imyme.mine.domain.challenge.scheduler.ChallengeScheduler;
import com.imyme.mine.global.scheduler.RetentionScheduler;
import com.imyme.mine.global.scheduler.ZombieCleanupScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 수동 트리거 (개발/테스트 전용)
 *
 * <p>배포 전 반드시 제거하거나 @Profile("local") 으로 제한할 것.
 * 엔드포인트 인증 없음 — 내부망/개발 서버에서만 사용.
 */
@Slf4j
@RestController
@RequestMapping("/test/batch")
@RequiredArgsConstructor
public class BatchTestController {

    private final RetentionScheduler retentionScheduler;
    private final ZombieCleanupScheduler zombieCleanupScheduler;
    private final ChallengeScheduler challengeScheduler;

    /** 03:00 — 만료 세션 삭제 */
    @PostMapping("/retention/expired-sessions")
    public String triggerExpiredSessions() {
        log.info("[BatchTest] 만료 세션 삭제 수동 트리거");
        retentionScheduler.deleteExpiredSessions();
        return "OK: expired sessions deleted";
    }

    /** 03:00 — 미사용 기기 Soft Delete */
    @PostMapping("/retention/inactive-devices")
    public String triggerInactiveDevices() {
        log.info("[BatchTest] 미사용 기기 Soft Delete 수동 트리거");
        retentionScheduler.softDeleteInactiveDevices();
        return "OK: inactive devices soft-deleted";
    }

    /** 04:00 — 탈퇴 회원 Hard Delete + S3 삭제 */
    @PostMapping("/retention/withdrawn-users")
    public String triggerWithdrawnUsers() {
        log.info("[BatchTest] 탈퇴 회원 Hard Delete 수동 트리거");
        retentionScheduler.hardDeleteWithdrawnUsers();
        return "OK: withdrawn users hard-deleted";
    }

    /** 04:00 — 삭제 카드 Hard Delete + S3 오디오 삭제 */
    @PostMapping("/retention/deleted-cards")
    public String triggerDeletedCards() {
        log.info("[BatchTest] 삭제 카드 Hard Delete 수동 트리거");
        retentionScheduler.hardDeleteSoftDeletedCards();
        return "OK: soft-deleted cards hard-deleted";
    }

    /** 04:00 — 읽은 알림 삭제 */
    @PostMapping("/retention/old-notifications")
    public String triggerOldNotifications() {
        log.info("[BatchTest] 읽은 알림 삭제 수동 트리거");
        retentionScheduler.deleteOldReadNotifications();
        return "OK: old read notifications deleted";
    }

    /** 05:00 — 알림 로그 삭제 */
    @PostMapping("/retention/notification-logs")
    public String triggerNotificationLogs() {
        log.info("[BatchTest] 알림 발송 로그 삭제 수동 트리거");
        retentionScheduler.deleteOldNotificationLogs();
        return "OK: old notification logs deleted";
    }

    /** 매 30분 — 유령 PvP 방 EXPIRED 처리 */
    @PostMapping("/zombie/ghost-rooms")
    public String triggerGhostRooms() {
        log.info("[BatchTest] 유령 PvP 방 EXPIRED 처리 수동 트리거");
        zombieCleanupScheduler.expireGhostRooms();
        return "OK: ghost rooms expired";
    }

    /** 매시간 — PvP PENDING 제출 삭제 */
    @PostMapping("/zombie/stale-submissions")
    public String triggerStaleSubmissions() {
        log.info("[BatchTest] PvP PENDING 제출 삭제 수동 트리거");
        zombieCleanupScheduler.deleteStalePendingSubmissions();
        return "OK: stale submissions deleted";
    }

    /** 04:10 — 유령 카드 Soft Delete */
    @PostMapping("/zombie/ghost-cards")
    public String triggerGhostCards() {
        log.info("[BatchTest] 유령 카드 Soft Delete 수동 트리거");
        zombieCleanupScheduler.softDeleteGhostCards();
        return "OK: ghost cards soft-deleted";
    }

    /** 00:05 — 내일 챌린지 생성 */
    @PostMapping("/challenge/create-tomorrow")
    public String triggerCreateTomorrowChallenge() {
        log.info("[BatchTest] 내일 챌린지 생성 수동 트리거");
        challengeScheduler.createTomorrowChallenge();
        return "OK: tomorrow challenge created";
    }

    /** 22:00 — 챌린지 OPEN */
    @PostMapping("/challenge/open")
    public String triggerOpenChallenge() {
        log.info("[BatchTest] 챌린지 OPEN 수동 트리거");
        challengeScheduler.openChallenge();
        return "OK: challenge opened";
    }

    /** 22:10 — 챌린지 CLOSED */
    @PostMapping("/challenge/close")
    public String triggerCloseChallenge() {
        log.info("[BatchTest] 챌린지 CLOSED 수동 트리거");
        challengeScheduler.closeChallenge();
        return "OK: challenge closed";
    }

    /** 22:12 — 챌린지 ANALYZING + MQ 발행 */
    @PostMapping("/challenge/start-analyzing")
    public String triggerStartAnalyzing() {
        log.info("[BatchTest] 챌린지 ANALYZING + MQ 발행 수동 트리거");
        challengeScheduler.startAnalyzing();
        return "OK: challenge analyzing started";
    }
}