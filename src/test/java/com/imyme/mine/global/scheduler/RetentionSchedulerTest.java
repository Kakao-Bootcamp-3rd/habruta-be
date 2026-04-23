package com.imyme.mine.global.scheduler;

import com.imyme.mine.domain.auth.repository.DeviceRepository;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.domain.notification.repository.NotificationLogRepository;
import com.imyme.mine.domain.notification.repository.NotificationRepository;
import com.imyme.mine.domain.storage.service.StorageService;
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

@DisplayName("RetentionScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class RetentionSchedulerTest {

    @Mock UserSessionRepository userSessionRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock UserRepository userRepository;
    @Mock CardRepository cardRepository;
    @Mock CardAttemptRepository cardAttemptRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationLogRepository notificationLogRepository;
    @Mock StorageService storageService;

    @InjectMocks
    RetentionScheduler scheduler;

    // =========================================================================
    // 03:00 배치
    // =========================================================================

    @Test
    @DisplayName("만료 세션 삭제 - 현재 시각 기준으로 repository 호출")
    void deleteExpiredSessions_callsRepositoryWithCurrentTime() {
        when(userSessionRepository.deleteExpiredSessions(any())).thenReturn(3);

        scheduler.deleteExpiredSessions();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userSessionRepository).deleteExpiredSessions(captor.capture());
        // 호출 시점 기준 1초 이내 (테스트 실행 시간 허용)
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("미사용 기기 Soft Delete - 6개월 이전 threshold 사용")
    void softDeleteInactiveDevices_uses6MonthThreshold() {
        when(deviceRepository.softDeleteInactiveDevices(any())).thenReturn(2);

        scheduler.softDeleteInactiveDevices();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deviceRepository).softDeleteInactiveDevices(captor.capture());
        // 6개월 이전 시각이어야 함
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusMonths(5));
    }

    // =========================================================================
    // 04:00 배치 — 탈퇴 회원
    // =========================================================================

    @Test
    @DisplayName("탈퇴 회원 Hard Delete - 대상 없으면 DB 삭제 미호출")
    void hardDeleteWithdrawnUsers_noTarget_skipsDelete() {
        when(userRepository.findDeletedUsersForHardDelete(any(), anyInt()))
            .thenReturn(List.of());

        scheduler.hardDeleteWithdrawnUsers();

        verify(userRepository, never()).hardDeleteByIds(anyList());
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("탈퇴 회원 Hard Delete - S3 삭제 실패 시 해당 유저 DB 삭제 건너뜀")
    void hardDeleteWithdrawnUsers_s3Fails_skipsDbDeleteForThatUser() {
        // 유저 1: 프로필 이미지 있고 S3 실패
        UserRepository.DeletedUserProjection user1 = mockProjection(1L, "profiles/1/img.jpg");
        // 유저 2: 프로필 이미지 없음 (정상 처리)
        UserRepository.DeletedUserProjection user2 = mockProjection(2L, null);

        when(userRepository.findDeletedUsersForHardDelete(any(), anyInt()))
            .thenReturn(List.of(user1, user2))
            .thenReturn(List.of()); // 2회차 빈 결과 → 루프 종료

        doThrow(new RuntimeException("S3 연결 실패"))
            .when(storageService).deleteObject("profiles/1/img.jpg");

        scheduler.hardDeleteWithdrawnUsers();

        // user1은 S3 실패로 건너뜀 → user2만 삭제
        verify(userRepository).hardDeleteByIds(List.of(2L));
    }

    @Test
    @DisplayName("탈퇴 회원 Hard Delete - S3 성공 시 두 유저 모두 DB 삭제")
    void hardDeleteWithdrawnUsers_s3Succeeds_deletesAllUsers() {
        UserRepository.DeletedUserProjection user1 = mockProjection(1L, "profiles/1/img.jpg");
        UserRepository.DeletedUserProjection user2 = mockProjection(2L, "profiles/2/img.jpg");

        when(userRepository.findDeletedUsersForHardDelete(any(), anyInt()))
            .thenReturn(List.of(user1, user2))
            .thenReturn(List.of());
        when(userRepository.hardDeleteByIds(anyList())).thenReturn(2);

        scheduler.hardDeleteWithdrawnUsers();

        verify(storageService).deleteObject("profiles/1/img.jpg");
        verify(storageService).deleteObject("profiles/2/img.jpg");
        verify(userRepository).hardDeleteByIds(List.of(1L, 2L));
    }

    @Test
    @DisplayName("탈퇴 회원 Hard Delete - 청크가 꽉 찼으면 다음 청크 조회")
    void hardDeleteWithdrawnUsers_fullChunk_fetchesNextChunk() {
        // 첫 번째 청크: 1000개 (꽉 참), 두 번째: 빈 청크
        List<UserRepository.DeletedUserProjection> fullChunk = buildChunk(1000);
        when(userRepository.findDeletedUsersForHardDelete(any(), anyInt()))
            .thenReturn(fullChunk)
            .thenReturn(List.of());
        when(userRepository.hardDeleteByIds(anyList())).thenReturn(1000);

        scheduler.hardDeleteWithdrawnUsers();

        // findDeletedUsersForHardDelete 2회 호출 (1차: 1000개, 2차: 빈 리스트)
        verify(userRepository, times(2)).findDeletedUsersForHardDelete(any(), anyInt());
    }

    // =========================================================================
    // 04:00 배치 — 카드
    // =========================================================================

    @Test
    @DisplayName("삭제 카드 Hard Delete - 오디오 키 S3 삭제 후 DB 삭제")
    void hardDeleteSoftDeletedCards_deletesAudioKeysBeforeDbDelete() {
        when(cardRepository.findDeletedCardIdsForHardDelete(any(), anyInt()))
            .thenReturn(List.of(10L, 20L))
            .thenReturn(List.of());
        when(cardAttemptRepository.findAudioKeysByCardIds(List.of(10L, 20L)))
            .thenReturn(List.of("audios/10/a.m4a", "audios/20/b.m4a"));
        when(storageService.deleteObjects(anyList())).thenReturn(List.of());
        when(cardRepository.hardDeleteByIds(anyList())).thenReturn(2);

        scheduler.hardDeleteSoftDeletedCards();

        verify(storageService).deleteObjects(List.of("audios/10/a.m4a", "audios/20/b.m4a"));
        verify(cardRepository).hardDeleteByIds(List.of(10L, 20L));
    }

    @Test
    @DisplayName("삭제 카드 Hard Delete - 오디오 키 없는 카드도 DB 삭제")
    void hardDeleteSoftDeletedCards_noAudioKeys_stillDeletesDb() {
        when(cardRepository.findDeletedCardIdsForHardDelete(any(), anyInt()))
            .thenReturn(List.of(10L))
            .thenReturn(List.of());
        when(cardAttemptRepository.findAudioKeysByCardIds(anyList())).thenReturn(List.of());
        when(cardRepository.hardDeleteByIds(anyList())).thenReturn(1);

        scheduler.hardDeleteSoftDeletedCards();

        verifyNoInteractions(storageService);
        verify(cardRepository).hardDeleteByIds(List.of(10L));
    }

    // =========================================================================
    // 04:00 배치 — 알림
    // =========================================================================

    @Test
    @DisplayName("읽은 알림 삭제 - 30일 이전 threshold 사용")
    void deleteOldReadNotifications_uses30DayThreshold() {
        when(notificationRepository.deleteOldReadNotifications(any())).thenReturn(10);

        scheduler.deleteOldReadNotifications();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).deleteOldReadNotifications(captor.capture());
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(29));
    }

    // =========================================================================
    // 05:00 배치 — 알림 로그
    // =========================================================================

    @Test
    @DisplayName("알림 로그 삭제 - 청크 1000건 미만이 나오면 루프 종료")
    void deleteOldNotificationLogs_stopsWhenChunkSmallerThanBatchSize() {
        when(notificationLogRepository.deleteOldLogs(any(), eq(1000)))
            .thenReturn(1000) // 1회차: 꽉 참 → 계속
            .thenReturn(400); // 2회차: 미만 → 종료

        scheduler.deleteOldNotificationLogs();

        verify(notificationLogRepository, times(2)).deleteOldLogs(any(), eq(1000));
    }

    @Test
    @DisplayName("알림 로그 삭제 - 90일 이전 threshold 사용")
    void deleteOldNotificationLogs_uses90DayThreshold() {
        when(notificationLogRepository.deleteOldLogs(any(), anyInt())).thenReturn(0);

        scheduler.deleteOldNotificationLogs();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationLogRepository).deleteOldLogs(captor.capture(), anyInt());
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(89));
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private UserRepository.DeletedUserProjection mockProjection(Long id, String imageKey) {
        UserRepository.DeletedUserProjection p = mock(UserRepository.DeletedUserProjection.class);
        when(p.getId()).thenReturn(id);
        when(p.getProfileImageKey()).thenReturn(imageKey);
        return p;
    }

    private List<UserRepository.DeletedUserProjection> buildChunk(int size) {
        return java.util.stream.IntStream.range(0, size)
            .mapToObj(i -> mockProjection((long) i, null))
            .toList();
    }
}