package com.imyme.mine.global.scheduler;

import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.entity.PvpSubmissionStatus;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.repository.PvpSubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ZombieCleanupScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ZombieCleanupSchedulerTest {

    @Mock PvpRoomRepository pvpRoomRepository;
    @Mock PvpSubmissionRepository pvpSubmissionRepository;
    @Mock CardRepository cardRepository;

    @InjectMocks
    ZombieCleanupScheduler scheduler;

    // =========================================================================
    // 유령 PvP 방 EXPIRED 처리
    // =========================================================================

    @Test
    @DisplayName("유령 방 만료 - OPEN/MATCHED/THINKING 3가지 상태를 대상으로 조회")
    void expireGhostRooms_targetsCorrectStatuses() {
        when(pvpRoomRepository.expireGhostRooms(any(), anyList(), any())).thenReturn(0);

        scheduler.expireGhostRooms();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PvpRoomStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
        verify(pvpRoomRepository).expireGhostRooms(
                eq(PvpRoomStatus.EXPIRED),
                statusCaptor.capture(),
                any(LocalDateTime.class)
        );

        List<PvpRoomStatus> captured = statusCaptor.getValue();
        assertThat(captured).containsExactlyInAnyOrder(
                PvpRoomStatus.OPEN, PvpRoomStatus.MATCHED, PvpRoomStatus.THINKING
        );
    }

    @Test
    @DisplayName("유령 방 만료 - 1시간 이전 threshold 사용")
    void expireGhostRooms_uses1HourThreshold() {
        when(pvpRoomRepository.expireGhostRooms(any(), anyList(), any())).thenReturn(0);

        scheduler.expireGhostRooms();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(pvpRoomRepository).expireGhostRooms(any(), anyList(), captor.capture());

        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusMinutes(59));
    }

    @Test
    @DisplayName("유령 방 만료 - 처리 건수 0이면 로그만 (정상 처리)")
    void expireGhostRooms_zeroResult_noException() {
        when(pvpRoomRepository.expireGhostRooms(any(), anyList(), any())).thenReturn(0);

        scheduler.expireGhostRooms(); // 예외 없이 정상 완료
    }

    // =========================================================================
    // PvP PENDING 제출 삭제
    // =========================================================================

    @Test
    @DisplayName("PENDING 제출 삭제 - PENDING 상태와 1시간 threshold로 호출")
    void deleteStalePendingSubmissions_usesCorrectParams() {
        when(pvpSubmissionRepository.deleteStaleSubmissions(any(), any())).thenReturn(2);

        scheduler.deleteStalePendingSubmissions();

        ArgumentCaptor<PvpSubmissionStatus> statusCaptor = ArgumentCaptor.forClass(PvpSubmissionStatus.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(pvpSubmissionRepository).deleteStaleSubmissions(statusCaptor.capture(), timeCaptor.capture());

        assertThat(statusCaptor.getValue()).isEqualTo(PvpSubmissionStatus.PENDING);
        assertThat(timeCaptor.getValue()).isBefore(LocalDateTime.now().minusMinutes(59));
    }

    @Test
    @DisplayName("PENDING 제출 삭제 - 삭제 건수 0이면 로그만 (정상 처리)")
    void deleteStalePendingSubmissions_zeroResult_noException() {
        when(pvpSubmissionRepository.deleteStaleSubmissions(any(), any())).thenReturn(0);

        scheduler.deleteStalePendingSubmissions(); // 예외 없이 정상 완료
    }

    // =========================================================================
    // 유령 카드 Soft Delete
    // =========================================================================

    @Test
    @DisplayName("유령 카드 Soft Delete - 7일 이전 threshold 사용")
    void softDeleteGhostCards_uses7DayThreshold() {
        when(cardRepository.softDeleteGhostCards(any())).thenReturn(5);

        scheduler.softDeleteGhostCards();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cardRepository).softDeleteGhostCards(captor.capture());

        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(6));
    }
}