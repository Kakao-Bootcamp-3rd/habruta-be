package com.imyme.mine.domain.pvp.service;

import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.repository.PvpSubmissionRepository;
import com.imyme.mine.domain.pvp.websocket.PvpReadyManager;
import com.imyme.mine.global.messaging.MessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PvpAsyncService 타이머 단위 테스트")
@ExtendWith(MockitoExtension.class)
class PvpAsyncServiceTimerTest {

    @Mock TaskScheduler taskScheduler;
    @Mock PvpAsyncService selfMock;

    @Mock PvpRoomRepository pvpRoomRepository;
    @Mock PvpSubmissionRepository pvpSubmissionRepository;
    @Mock KeywordRepository keywordRepository;
    @Mock MessagePublisher messagePublisher;
    @Mock PvpMqConsumerService pvpMqConsumerService;
    @Mock PvpReadyManager pvpReadyManager;

    @Mock ScheduledFuture future1;
    @Mock ScheduledFuture future2;

    PvpAsyncService pvpAsyncService;

    @BeforeEach
    void setUp() {
        pvpAsyncService = new PvpAsyncService(
                pvpRoomRepository, pvpSubmissionRepository, keywordRepository,
                messagePublisher, pvpMqConsumerService, pvpReadyManager
        );
        ReflectionTestUtils.setField(pvpAsyncService, "taskScheduler", taskScheduler);
        ReflectionTestUtils.setField(pvpAsyncService, "self", selfMock);
    }

    // =========================================================================
    // 예약 기본 동작
    // =========================================================================

    @Test
    @DisplayName("scheduleThinkingTransition - schedule() 1회 호출")
    void scheduleThinkingTransition_callsScheduleOnce() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(future1);

        pvpAsyncService.scheduleThinkingTransition(1L);

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("Runnable 실행 시 self.doThinkingTransition() 호출")
    void runnableExecution_callsDoThinkingTransition() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(runnableCaptor.capture(), any(Instant.class))).thenReturn(future1);

        pvpAsyncService.scheduleThinkingTransition(42L);
        runnableCaptor.getValue().run();

        verify(selfMock).doThinkingTransition(42L);
    }

    // =========================================================================
    // 재예약 — 이전 future 취소
    // =========================================================================

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("재예약 시 이전 future cancel(false) 호출")
    void reschedule_cancelsPreviousFuture() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(future1)
                .thenReturn(future2);

        pvpAsyncService.scheduleThinkingTransition(1L);
        pvpAsyncService.scheduleThinkingTransition(1L);

        verify(future1).cancel(false);
    }

    // =========================================================================
    // 취소 — map 정리
    // =========================================================================

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("cancelTimer - future 취소 + pendingTimers에서 제거")
    void cancelTimer_cancelsFutureAndRemovesFromMap() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(future1);
        pvpAsyncService.scheduleThinkingTransition(1L);

        pvpAsyncService.cancelTimer(1L, PvpAsyncService.TimerType.THINKING_TRANSITION);

        verify(future1).cancel(false);
        ConcurrentHashMap<?, ?> pendingTimers =
                (ConcurrentHashMap<?, ?>) ReflectionTestUtils.getField(pvpAsyncService, "pendingTimers");
        assertThat(pendingTimers).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("cancelAllTimers - 모든 타입 future 취소 + map 비움")
    void cancelAllTimers_cancelsAllAndClearsMap() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(future1)
                .thenReturn(future2)
                .thenReturn(mock(ScheduledFuture.class));

        pvpAsyncService.scheduleThinkingTransition(1L);
        pvpAsyncService.scheduleRecordingTransition(1L, java.time.LocalDateTime.now());
        pvpAsyncService.scheduleRecordingTimeout(1L);

        pvpAsyncService.cancelAllTimers(1L);

        verify(future1).cancel(false);
        verify(future2).cancel(false);
        ConcurrentHashMap<?, ?> pendingTimers =
                (ConcurrentHashMap<?, ?>) ReflectionTestUtils.getField(pvpAsyncService, "pendingTimers");
        assertThat(pendingTimers).isEmpty();
    }

    // =========================================================================
    // 늦은 Runnable — 새 future 매핑 보호
    // =========================================================================

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("이전 Runnable이 늦게 실행돼도 새 future 매핑 유지")
    void lateRunnable_doesNotRemoveNewFutureFromMap() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(runnableCaptor.capture(), any(Instant.class)))
                .thenReturn(future1)
                .thenReturn(future2);

        pvpAsyncService.scheduleThinkingTransition(1L);
        pvpAsyncService.scheduleThinkingTransition(1L); // 재예약 → future2가 map에 등록됨

        Runnable oldRunnable = runnableCaptor.getAllValues().get(0);
        oldRunnable.run(); // 이전 Runnable이 늦게 실행됨

        // 새 future2 매핑은 그대로 유지되어야 함
        ConcurrentHashMap<?, ScheduledFuture<?>> pendingTimers =
                (ConcurrentHashMap<?, ScheduledFuture<?>>) ReflectionTestUtils.getField(pvpAsyncService, "pendingTimers");
        assertThat(pendingTimers).hasSize(1);
        assertThat(pendingTimers.values()).contains(future2);
    }

    // =========================================================================
    // null future 방어
    // =========================================================================

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("schedule() null 반환 시 pendingTimers에 등록되지 않음")
    void nullFuture_notAddedToMap() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(null);

        pvpAsyncService.scheduleThinkingTransition(1L);

        ConcurrentHashMap<?, ?> pendingTimers =
                (ConcurrentHashMap<?, ?>) ReflectionTestUtils.getField(pvpAsyncService, "pendingTimers");
        assertThat(pendingTimers).isEmpty();
    }
}