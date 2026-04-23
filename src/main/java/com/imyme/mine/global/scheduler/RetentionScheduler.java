package com.imyme.mine.global.scheduler;

import com.imyme.mine.domain.auth.repository.DeviceRepository;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.domain.notification.repository.NotificationLogRepository;
import com.imyme.mine.domain.notification.repository.NotificationRepository;
import com.imyme.mine.domain.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 데이터 보존 주기(Retention) 배치 스케줄러
 *
 * <p>S3 삭제 원칙: S3 DeleteObject 성공 후 DB Hard Delete.
 * S3 삭제 실패 시 해당 항목 DB 삭제를 건너뛰어 다음 배치에서 재시도 가능하도록 보존.
 *
 * <pre>
 * 03:00 — 만료 세션 삭제, 미사용 기기 Soft Delete
 * 04:00 — 탈퇴 회원 Hard Delete + S3 프로필 삭제
 *       — 삭제 카드 Hard Delete + S3 오디오 삭제
 *       — 읽은 알림 Hard Delete (30일 경과)
 * 05:00 — 알림 발송 로그 Hard Delete (90일 경과, 청크 처리)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final UserSessionRepository userSessionRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final CardAttemptRepository cardAttemptRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final StorageService storageService;

    private static final int RETENTION_DAYS_USER = 30;
    private static final int RETENTION_DAYS_CARD = 30;
    private static final int RETENTION_DAYS_NOTIFICATION = 30;
    private static final int RETENTION_DAYS_NOTIFICATION_LOG = 90;
    private static final int DEVICE_INACTIVE_MONTHS = 6;
    private static final int CHUNK_SIZE = 1000;

    // -------------------------------------------------------------------------
    // 03:00
    // -------------------------------------------------------------------------

    /**
     * 만료된 세션 일괄 Hard Delete
     * expires_at < NOW() 조건으로 멱등성 보장
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void deleteExpiredSessions() {
        int deleted = userSessionRepository.deleteExpiredSessions(LocalDateTime.now());
        log.info("[Retention] 만료 세션 삭제 완료 - {}건", deleted);
    }

    /**
     * 6개월 이상 미활성 기기 Soft Delete
     * 실제 Hard Delete는 탈퇴 회원 배치(04:00)의 users CASCADE에 포함
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void softDeleteInactiveDevices() {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(DEVICE_INACTIVE_MONTHS);
        int updated = deviceRepository.softDeleteInactiveDevices(threshold);
        log.info("[Retention] 미사용 기기 Soft Delete 완료 - {}건", updated);
    }

    // -------------------------------------------------------------------------
    // 04:00
    // -------------------------------------------------------------------------

    /**
     * 탈퇴 회원 Hard Delete + S3 프로필 이미지 삭제
     *
     * <p>처리 순서:
     * <ol>
     *   <li>삭제 대상 유저 청크 조회 (native query로 @SQLRestriction 우회)</li>
     *   <li>S3 프로필 이미지 삭제 (실패 시 해당 유저 DB 삭제 건너뜀)</li>
     *   <li>S3 성공 유저만 DB Hard Delete</li>
     * </ol>
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void hardDeleteWithdrawnUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS_USER);
        int totalDeleted = 0;

        List<UserRepository.DeletedUserProjection> chunk;
        do {
            chunk = userRepository.findDeletedUsersForHardDelete(threshold, CHUNK_SIZE);
            if (chunk.isEmpty()) {
                break;
            }

            List<Long> successIds = new ArrayList<>();
            for (UserRepository.DeletedUserProjection user : chunk) {
                String imageKey = user.getProfileImageKey();
                if (imageKey != null) {
                    try {
                        storageService.deleteObject(imageKey);
                    } catch (Exception e) {
                        log.warn("[Retention] S3 프로필 이미지 삭제 실패, DB 삭제 건너뜀 - userId: {}, key: {}",
                            user.getId(), imageKey, e);
                        continue; // S3 실패 → DB 삭제 스킵 (다음 배치 재시도)
                    }
                }
                successIds.add(user.getId());
            }

            if (!successIds.isEmpty()) {
                int deleted = userRepository.hardDeleteByIds(successIds);
                totalDeleted += deleted;
            }
        } while (chunk.size() == CHUNK_SIZE);

        log.info("[Retention] 탈퇴 회원 Hard Delete 완료 - 총 {}건", totalDeleted);
    }

    /**
     * Soft Delete된 카드 Hard Delete + S3 오디오 파일 삭제
     *
     * <p>처리 순서:
     * <ol>
     *   <li>삭제 대상 카드 ID 청크 조회 (native query로 @SQLRestriction 우회)</li>
     *   <li>해당 카드의 card_attempts.audio_key 목록 조회</li>
     *   <li>S3 Bulk DeleteObjects (실패 키 로그 후 계속 — 고아 파일 허용)</li>
     *   <li>카드 DB Hard Delete (cascade: card_attempts, card_feedbacks)</li>
     * </ol>
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void hardDeleteSoftDeletedCards() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS_CARD);
        int totalDeleted = 0;

        List<Long> cardIds;
        do {
            cardIds = cardRepository.findDeletedCardIdsForHardDelete(threshold, CHUNK_SIZE);
            if (cardIds.isEmpty()) {
                break;
            }

            // 오디오 키 S3 Bulk 삭제 (1000개 단위 청크)
            List<String> audioKeys = cardAttemptRepository.findAudioKeysByCardIds(cardIds);
            if (!audioKeys.isEmpty()) {
                for (int i = 0; i < audioKeys.size(); i += CHUNK_SIZE) {
                    List<String> keysChunk = audioKeys.subList(i, Math.min(i + CHUNK_SIZE, audioKeys.size()));
                    storageService.deleteObjects(keysChunk); // 실패 키는 내부 로그 처리
                }
            }

            // DB Hard Delete (S3 일부 실패해도 DB는 삭제 — 고아 파일보다 좀비 DB 레코드가 더 위험)
            int deleted = cardRepository.hardDeleteByIds(cardIds);
            totalDeleted += deleted;
        } while (cardIds.size() == CHUNK_SIZE);

        log.info("[Retention] 삭제 카드 Hard Delete 완료 - 총 {}건", totalDeleted);
    }

    /**
     * 읽은 알림 Hard Delete (30일 경과)
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void deleteOldReadNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS_NOTIFICATION);
        int deleted = notificationRepository.deleteOldReadNotifications(threshold);
        log.info("[Retention] 읽은 알림 삭제 완료 - {}건", deleted);
    }

    // -------------------------------------------------------------------------
    // 05:00
    // -------------------------------------------------------------------------

    /**
     * 알림 발송 로그 Hard Delete (90일 경과, 1000건 단위 청크 처리)
     * 대용량 테이블 Long Lock 방지를 위해 청크 반복 처리
     */
    @Scheduled(cron = "0 0 5 * * *")
    public void deleteOldNotificationLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS_NOTIFICATION_LOG);
        int totalDeleted = 0;

        int deleted;
        do {
            deleted = notificationLogRepository.deleteOldLogs(threshold, CHUNK_SIZE);
            totalDeleted += deleted;
        } while (deleted == CHUNK_SIZE);

        log.info("[Retention] 알림 발송 로그 삭제 완료 - 총 {}건", totalDeleted);
    }
}