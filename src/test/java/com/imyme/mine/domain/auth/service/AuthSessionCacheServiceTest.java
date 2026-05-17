package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthSessionCacheService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AuthSessionCacheServiceTest {

    private static final Long USER_ID = 1L;
    private static final String DEVICE_UUID = "device-1";
    private static final String CACHE_KEY = "auth:session:active:" + USER_ID;
    private static final String DEVICE_CACHE_KEY = CACHE_KEY + ":" + DEVICE_UUID;

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock UserSessionRepository userSessionRepository;
    @Mock AuthSessionOutboxService authSessionOutboxService;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Redis hit이면 DB 조회 없이 활성 세션으로 판단한다")
    void hasActiveSession_returnsTrueWithoutDatabaseWhenRedisHit() {
        AuthSessionCacheService service = newService();
        when(redisTemplate.hasKey(CACHE_KEY)).thenReturn(true);

        boolean result = service.hasActiveSession(USER_ID);

        assertThat(result).isTrue();
        verify(userSessionRepository, never()).findMaxExpiresAtByUserId(any());
    }

    @Test
    @DisplayName("Redis miss이면 DB 만료 시각으로 fallback하고 캐시를 다시 채운다")
    void hasActiveSession_fallsBackToDatabaseAndBackfillsCacheWhenRedisMiss() {
        AuthSessionCacheService service = newService();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        when(redisTemplate.hasKey(CACHE_KEY)).thenReturn(false);
        when(userSessionRepository.findMaxExpiresAtByUserId(USER_ID)).thenReturn(Optional.of(expiresAt));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        boolean result = service.hasActiveSession(USER_ID);

        assertThat(result).isTrue();
        verify(userSessionRepository).findMaxExpiresAtByUserId(USER_ID);
        verify(valueOperations).set(eq(CACHE_KEY), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("기기 세션 Redis hit이면 DB 조회 없이 활성 세션으로 판단한다")
    void hasActiveSessionWithDevice_returnsTrueWithoutDatabaseWhenRedisHit() {
        AuthSessionCacheService service = newService();
        when(redisTemplate.hasKey(DEVICE_CACHE_KEY)).thenReturn(true);

        boolean result = service.hasActiveSession(USER_ID, DEVICE_UUID);

        assertThat(result).isTrue();
        verify(userSessionRepository, never()).findExpiresAtByUserIdAndDeviceUuid(any(), any());
    }

    @Test
    @DisplayName("기기 세션 Redis miss이면 userId와 deviceUuid로 DB fallback한다")
    void hasActiveSessionWithDevice_fallsBackToDatabaseAndBackfillsCacheWhenRedisMiss() {
        AuthSessionCacheService service = newService();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        when(redisTemplate.hasKey(DEVICE_CACHE_KEY)).thenReturn(false);
        when(userSessionRepository.findExpiresAtByUserIdAndDeviceUuid(USER_ID, DEVICE_UUID))
            .thenReturn(Optional.of(expiresAt));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        boolean result = service.hasActiveSession(USER_ID, DEVICE_UUID);

        assertThat(result).isTrue();
        verify(userSessionRepository).findExpiresAtByUserIdAndDeviceUuid(USER_ID, DEVICE_UUID);
        verify(valueOperations).set(eq(DEVICE_CACHE_KEY), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("DB fallback 결과가 만료된 세션이면 false를 반환하고 캐시를 삭제한다")
    void hasActiveSession_returnsFalseAndEvictsCacheWhenDatabaseSessionExpired() {
        AuthSessionCacheService service = newService();
        LocalDateTime expiresAt = LocalDateTime.now().minusMinutes(1);

        when(redisTemplate.hasKey(CACHE_KEY)).thenReturn(false);
        when(userSessionRepository.findMaxExpiresAtByUserId(USER_ID)).thenReturn(Optional.of(expiresAt));

        boolean result = service.hasActiveSession(USER_ID);

        assertThat(result).isFalse();
        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("Redis 조회 실패 시 DB fallback으로 활성 세션을 판단한다")
    void hasActiveSession_fallsBackToDatabaseWhenRedisReadFails() {
        AuthSessionCacheService service = newService();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        when(redisTemplate.hasKey(CACHE_KEY)).thenThrow(new IllegalStateException("redis down"));
        when(userSessionRepository.findMaxExpiresAtByUserId(USER_ID)).thenReturn(Optional.of(expiresAt));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        boolean result = service.hasActiveSession(USER_ID);

        assertThat(result).isTrue();
        verify(userSessionRepository).findMaxExpiresAtByUserId(USER_ID);
        verify(valueOperations).set(eq(CACHE_KEY), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("markActiveAfterCommit 호출 시 Redis SET outbox 이벤트를 기록한다")
    void markActiveAfterCommit_enqueuesOutboxEvent() {
        AuthSessionCacheService service = newService();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        service.markActiveAfterCommit(USER_ID, expiresAt);

        verify(authSessionOutboxService).enqueueSetUserActive(USER_ID, expiresAt);
    }

    @Test
    @DisplayName("기기 세션 markActiveAfterCommit 호출 시 Redis SET outbox 이벤트를 기록한다")
    void markActiveAfterCommitWithDevice_enqueuesOutboxEvent() {
        AuthSessionCacheService service = newService();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        service.markActiveAfterCommit(USER_ID, DEVICE_UUID, expiresAt);

        verify(authSessionOutboxService).enqueueSetDeviceActive(USER_ID, DEVICE_UUID, expiresAt);
    }

    @Test
    @DisplayName("refreshAfterCommit에서 활성 세션이 없으면 커밋 이후 Redis DELETE outbox 이벤트를 기록한다")
    void refreshAfterCommit_enqueuesEvictAfterTransactionCommitWhenNoActiveSession() {
        AuthSessionCacheService service = newService();

        TransactionSynchronizationManager.initSynchronization();
        when(userSessionRepository.findMaxExpiresAtByUserId(USER_ID)).thenReturn(Optional.empty());

        service.refreshAfterCommit(USER_ID);

        verify(authSessionOutboxService, never()).enqueueDeleteUserActive(any());

        TransactionSynchronizationUtils.triggerAfterCommit();

        verify(authSessionOutboxService).enqueueDeleteUserActive(USER_ID);
    }

    private AuthSessionCacheService newService() {
        return new AuthSessionCacheService(redisTemplate, userSessionRepository, authSessionOutboxService);
    }
}
