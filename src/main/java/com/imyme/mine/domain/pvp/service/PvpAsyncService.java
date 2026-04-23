package com.imyme.mine.domain.pvp.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.entity.PvpSubmission;
import com.imyme.mine.domain.pvp.entity.PvpSubmissionStatus;
import com.imyme.mine.domain.pvp.messaging.PvpChannels;
import com.imyme.mine.domain.pvp.messaging.PvpMessage;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.repository.PvpSubmissionRepository;
import com.imyme.mine.global.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * PvP 비동기 작업 서비스
 *
 * <p>Thread.sleep 대신 TaskScheduler 기반 예약 실행으로 스레드 블로킹을 제거한다.
 * TimerKey + ConcurrentHashMap으로 예약 핸들을 관리하며, 재예약 시 이전 예약을 취소한다.
 * stale 타이머 방어 로직(status 검증, startedAt 비교)은 doXxx 메서드에서 이중 안전장치로 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PvpAsyncService {

    private static final long THINKING_TRANSITION_MILLIS  = 3_000L;
    private static final long RECORDING_TRANSITION_MILLIS = 30_000L;
    private static final long RECORDING_TIMEOUT_MILLIS    = 80_000L;

    /**
     * 타이머 종류 — PvpRoomService에서 취소 시 사용
     */
    public enum TimerType {
        THINKING_TRANSITION, RECORDING_TRANSITION, RECORDING_TIMEOUT
    }

    private record TimerKey(Long roomId, TimerType type) {}

    private final PvpRoomRepository pvpRoomRepository;
    private final PvpSubmissionRepository pvpSubmissionRepository;
    private final KeywordRepository keywordRepository;
    private final MessagePublisher messagePublisher;
    private final PvpMqConsumerService pvpMqConsumerService;
    private final com.imyme.mine.domain.pvp.websocket.PvpReadyManager pvpReadyManager;

    // @RequiredArgsConstructor로 @Qualifier를 지정할 수 없으므로 필드 주입
    @Autowired
    @Qualifier("pvpTimerScheduler")
    private TaskScheduler taskScheduler;

    // Self-injection: 내부 @Transactional 메서드 호출 시 프록시를 거치도록
    @Lazy
    @Autowired
    private PvpAsyncService self;

    // 예약 핸들 맵 — 재예약·취소 시 이전 future 관리
    private final ConcurrentHashMap<TimerKey, ScheduledFuture<?>> pendingTimers = new ConcurrentHashMap<>();

    // ===== 타이머 예약 =====

    /**
     * 3초 후 THINKING 전환 예약
     */
    public void scheduleThinkingTransition(Long roomId) {
        scheduleTimer(
                new TimerKey(roomId, TimerType.THINKING_TRANSITION),
                () -> self.doThinkingTransition(roomId),
                THINKING_TRANSITION_MILLIS
        );
    }

    /**
     * 30초 후 RECORDING 전환 예약
     * - thinkingStartedAt: stale 타이머 감지용 (게스트 재입장 시 이전 페이즈 타이머 스킵)
     */
    public void scheduleRecordingTransition(Long roomId, LocalDateTime thinkingStartedAt) {
        scheduleTimer(
                new TimerKey(roomId, TimerType.RECORDING_TRANSITION),
                () -> self.doRecordingTransition(roomId, thinkingStartedAt),
                RECORDING_TRANSITION_MILLIS
        );
    }

    /**
     * 80초 후 RECORDING 타임아웃 예약
     */
    public void scheduleRecordingTimeout(Long roomId) {
        scheduleTimer(
                new TimerKey(roomId, TimerType.RECORDING_TIMEOUT),
                () -> self.doRecordingTimeout(roomId),
                RECORDING_TIMEOUT_MILLIS
        );
    }

    // ===== 타이머 취소 =====

    /**
     * 단건 취소
     */
    public void cancelTimer(Long roomId, TimerType type) {
        TimerKey key = new TimerKey(roomId, type);
        ScheduledFuture<?> future = pendingTimers.remove(key);
        if (future != null) {
            future.cancel(false);
            log.info("[PvP 타이머] 취소: roomId={}, type={}", roomId, type);
        }
    }

    /**
     * 방 전체 타이머 취소 — 방 종료·게스트 이탈 시 호출
     */
    public void cancelAllTimers(Long roomId) {
        for (TimerType type : TimerType.values()) {
            cancelTimer(roomId, type);
        }
        log.info("[PvP 타이머] 전체 취소: roomId={}", roomId);
    }

    // ===== 상태 전환 메서드 =====

    /**
     * THINKING 전환 DB 작업
     * - 비관적 락: leaveRoom과 직렬화하여 낙관적 잠금 충돌 방지
     */
    @Transactional
    public void doThinkingTransition(Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetailsForUpdate(roomId).orElse(null);

        if (room == null || room.getStatus() != PvpRoomStatus.MATCHED) {
            log.warn("THINKING 전환 실패: 방 상태 불일치 - roomId={}", roomId);
            return;
        }

        List<Keyword> keywords = keywordRepository.findAllByCategoryIdAndIsActiveOrderByDisplayOrderAsc(
                room.getCategory().getId(), true);

        if (keywords.isEmpty()) {
            log.error("THINKING 전환 실패: 키워드 없음 - roomId={}, categoryId={}", roomId, room.getCategory().getId());
            return;
        }

        Keyword randomKeyword = keywords.get((int) (Math.random() * keywords.size()));
        room.startThinking(randomKeyword);

        pvpRoomRepository.save(room);
        log.info("THINKING 전환 완료: roomId={}, keywordId={}", roomId, randomKeyword.getId());

        final Long keywordId = randomKeyword.getId();
        final String keywordName = randomKeyword.getName();
        final var startedAt = room.getStartedAt();
        final var thinkingEndsAt = startedAt != null ? startedAt.plusSeconds(30) : null;

        // 커밋 후 Redis Pub/Sub 발행 + RECORDING 전환 타이머 예약
        afterCommit(() -> {
            messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                    PvpMessage.thinkingStarted(roomId, keywordId, keywordName, startedAt, thinkingEndsAt));
            self.scheduleRecordingTransition(roomId, startedAt);
        });
    }

    /**
     * RECORDING 전환 DB 작업
     * - 비관적 락: leaveRoom과 직렬화하여 레이스 컨디션 방지
     * - expectedStartedAt: stale 타이머 감지 (게스트 퇴장 후 재입장 시 이전 페이즈 스킵)
     */
    @Transactional
    public void doRecordingTransition(Long roomId, LocalDateTime expectedStartedAt) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetailsForUpdate(roomId).orElse(null);

        if (room == null) {
            log.warn("RECORDING 전환 실패: 방 없음 - roomId={}", roomId);
            return;
        }

        // 이미 RECORDING 이상이면 스킵 (READY로 조기 전환된 경우)
        if (room.getStatus() != PvpRoomStatus.THINKING) {
            log.info("RECORDING 타이머 스킵: 이미 전환됨 - roomId={}, status={}", roomId, room.getStatus());
            pvpReadyManager.clearReady(roomId);
            return;
        }

        // stale 타이머 방어: 다른 THINKING 페이즈의 타이머 스킵
        if (!expectedStartedAt.equals(room.getStartedAt())) {
            log.info("RECORDING 타이머 스킵: 다른 THINKING 페이즈 - roomId={}, expected={}, actual={}",
                    roomId, expectedStartedAt, room.getStartedAt());
            pvpReadyManager.clearReady(roomId);
            return;
        }

        room.startRecording();
        pvpRoomRepository.save(room);
        pvpReadyManager.clearReady(roomId);
        log.info("RECORDING 타이머 자동 전환 완료: roomId={}", roomId);

        // 커밋 후 Redis Pub/Sub 발행 + 타임아웃 예약
        afterCommit(() -> {
            messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                    PvpMessage.recordingStarted(roomId));
            self.scheduleRecordingTimeout(roomId);
        });
    }

    /**
     * RECORDING 타임아웃 핸들러
     * - 미제출자 FAILED 처리 후 PROCESSING 또는 CANCELED 전환
     */
    @Transactional
    public void doRecordingTimeout(Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetailsForUpdate(roomId).orElse(null);
        if (room == null) {
            log.warn("[Timeout] 방 없음: roomId={}", roomId);
            return;
        }

        // 이미 RECORDING이 아니면 스킵 (정상 완료됨)
        if (room.getStatus() != PvpRoomStatus.RECORDING) {
            log.info("[Timeout] 스킵: 이미 전환됨 - roomId={}, status={}", roomId, room.getStatus());
            return;
        }

        User host = room.getHostUser();
        User guest = room.getGuestUser();
        if (host == null || guest == null) {
            log.warn("[Timeout] host/guest null: roomId={}", roomId);
            return;
        }

        List<PvpSubmission> submissions = pvpSubmissionRepository.findByRoomIdWithUser(roomId);
        Map<Long, PvpSubmission> submissionMap = submissions.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        int submittedCount = 0;
        for (User user : List.of(host, guest)) {
            PvpSubmission s = submissionMap.get(user.getId());

            if (s == null) {
                try {
                    PvpSubmission newS = PvpSubmission.builder()
                            .room(room)
                            .user(user)
                            .build();
                    newS.fail();
                    pvpSubmissionRepository.save(newS);
                    log.info("[Timeout] 미제출 FAILED 레코드 생성: roomId={}, userId={}", roomId, user.getId());
                } catch (DataIntegrityViolationException e) {
                    log.info("[Timeout] submission 동시 생성 감지: roomId={}, userId={}", roomId, user.getId());
                }
                continue;
            }

            if (s.getStatus() == PvpSubmissionStatus.PENDING) {
                s.fail();
                pvpSubmissionRepository.save(s);
                log.info("[Timeout] PENDING → FAILED: roomId={}, userId={}", roomId, user.getId());
                continue;
            }

            // UPLOADED, PROCESSING, COMPLETED → 제출한 것으로 간주
            submittedCount++;
        }

        if (submittedCount == 0) {
            room.cancel();
            pvpRoomRepository.save(room);
            log.info("[Timeout] 양쪽 미제출 → CANCELED: roomId={}", roomId);

            afterCommit(() ->
                    messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                            PvpMessage.statusChange(roomId, PvpRoomStatus.CANCELED,
                                    "제출 시간이 초과되어 방이 취소되었습니다."))
            );
            return;
        }

        // 1명 이상 제출 → PROCESSING 전환
        room.startProcessing();
        pvpRoomRepository.save(room);
        log.info("[Timeout] PROCESSING 전환: roomId={}, submittedCount={}", roomId, submittedCount);

        // 트랜잭션 안에서 Feedback Request 발행 (비관적 락 필요)
        pvpMqConsumerService.tryPublishFeedbackRequest(roomId);

        afterCommit(() ->
                messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                        PvpMessage.statusChange(roomId, PvpRoomStatus.PROCESSING,
                                "제출 시간이 종료되었습니다. AI 분석을 시작합니다."))
        );
    }

    // ===== 내부 헬퍼 =====

    /**
     * 타이머 예약 — 재예약 시 이전 future 취소, 늦은 실행 시 map 무결성 보장
     *
     * <p>AtomicReference로 currentFuture를 캡처하여 pendingTimers.remove(key, currentFuture)를 수행한다.
     * 재예약으로 map이 교체된 뒤 이전 Runnable이 늦게 실행되더라도 새 future 매핑을 덮어쓰지 않는다.
     */
    private void scheduleTimer(TimerKey key, Runnable task, long delayMillis) {
        ScheduledFuture<?> oldFuture = pendingTimers.remove(key);
        if (oldFuture != null) {
            oldFuture.cancel(false);
            log.debug("[PvP 타이머] 기존 예약 취소 후 재예약: key={}", key);
        }

        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        ScheduledFuture<?> newFuture = taskScheduler.schedule(
                () -> {
                    pendingTimers.remove(key, futureRef.get());
                    task.run();
                },
                Instant.now().plusMillis(delayMillis)
        );

        if (newFuture == null) {
            log.warn("[PvP 타이머] 예약 실패 (null 반환): key={}", key);
            return;
        }

        futureRef.set(newFuture);
        pendingTimers.put(key, newFuture);
        log.info("[PvP 타이머] 예약 완료: key={}, delayMs={}", key, delayMillis);
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}