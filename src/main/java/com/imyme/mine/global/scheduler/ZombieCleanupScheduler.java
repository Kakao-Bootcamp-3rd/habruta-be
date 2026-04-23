package com.imyme.mine.global.scheduler;

import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.entity.PvpSubmissionStatus;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.repository.PvpSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 좀비 데이터 정리 배치 스케줄러
 *
 * <p>이탈·에러로 중간 상태에 멈춘 데이터를 정리하여 DB 무결성을 유지.
 *
 * <pre>
 * 매 30분 — 유령 PvP 방 EXPIRED 처리 (OPEN/MATCHED/THINKING 1시간 초과)
 * 매시간  — PvP PENDING 제출 Hard Delete (1시간 초과)
 * 04:10   — 유령 카드 Soft Delete (attempt_count=0, 7일 경과)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZombieCleanupScheduler {

    private final PvpRoomRepository pvpRoomRepository;
    private final PvpSubmissionRepository pvpSubmissionRepository;
    private final CardRepository cardRepository;

    private static final int GHOST_ROOM_TIMEOUT_HOURS = 1;
    private static final int GHOST_SUBMISSION_TIMEOUT_HOURS = 1;
    private static final int GHOST_CARD_DAYS = 7;

    /** 유령 방 판정 대상 상태 */
    private static final List<PvpRoomStatus> GHOST_ROOM_STATUSES =
            List.of(PvpRoomStatus.OPEN, PvpRoomStatus.MATCHED, PvpRoomStatus.THINKING);

    // -------------------------------------------------------------------------
    // 매 30분
    // -------------------------------------------------------------------------

    /**
     * 유령 PvP 방 EXPIRED 처리
     *
     * <p>OPEN / MATCHED / THINKING 상태에서 1시간을 초과한 방은 이미 이탈된 것으로 판단.
     * Bulk UPDATE로 처리하므로 {@code @Version} (낙관적 잠금) 미적용.
     * 해당 방들은 이미 이탈된 상태이므로 동시성 충돌 위험 없음.
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void expireGhostRooms() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(GHOST_ROOM_TIMEOUT_HOURS);

        int expired = pvpRoomRepository.expireGhostRooms(
                PvpRoomStatus.EXPIRED,
                GHOST_ROOM_STATUSES,
                threshold
        );

        if (expired > 0) {
            log.info("[ZombieCleanup] 유령 PvP 방 EXPIRED 처리 완료 - {}건", expired);
        }
    }

    // -------------------------------------------------------------------------
    // 매시간
    // -------------------------------------------------------------------------

    /**
     * PvP PENDING 제출 Hard Delete
     *
     * <p>PENDING 상태는 Presigned URL 발급 전이므로 audio_url 없음 → S3 삭제 불필요.
     * 1시간 초과 PENDING 제출은 업로드 포기로 판단하여 삭제.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void deleteStalePendingSubmissions() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(GHOST_SUBMISSION_TIMEOUT_HOURS);

        int deleted = pvpSubmissionRepository.deleteStaleSubmissions(
                PvpSubmissionStatus.PENDING,
                threshold
        );

        if (deleted > 0) {
            log.info("[ZombieCleanup] PvP PENDING 제출 삭제 완료 - {}건", deleted);
        }
    }

    // -------------------------------------------------------------------------
    // 04:10
    // -------------------------------------------------------------------------

    /**
     * 유령 카드 Soft Delete
     *
     * <p>한 번도 시도하지 않은 카드(attempt_count=0)가 7일 이상 방치된 경우.
     * Soft Delete 후 30일 경과 시 {@link RetentionScheduler}의 Hard Delete 대상이 됨.
     * attempt_count=0 → 오디오 파일 없음 → S3 삭제 불필요.
     */
    @Scheduled(cron = "0 10 4 * * *")
    public void softDeleteGhostCards() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(GHOST_CARD_DAYS);

        int deleted = cardRepository.softDeleteGhostCards(threshold);

        log.info("[ZombieCleanup] 유령 카드 Soft Delete 완료 - {}건", deleted);
    }
}