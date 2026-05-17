package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.entity.AuthSessionOutboxEvent;
import com.imyme.mine.domain.auth.entity.AuthSessionOutboxEventType;
import com.imyme.mine.domain.auth.entity.AuthSessionOutboxStatus;
import com.imyme.mine.domain.auth.repository.AuthSessionOutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthSessionOutboxService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AuthSessionOutboxServiceTest {

    private static final Long USER_ID = 1L;
    private static final String DEVICE_UUID = "device-1";
    private static final String DEVICE_CACHE_KEY = "auth:session:active:" + USER_ID + ":" + DEVICE_UUID;

    @Mock AuthSessionOutboxEventRepository outboxEventRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("기기 세션 SET 이벤트를 저장하고 Redis에 즉시 반영한다")
    void enqueueSetDeviceActive_savesOutboxAndWritesRedis() {
        AuthSessionOutboxService service = newService();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        AuthSessionOutboxEvent savedEvent = AuthSessionOutboxEvent.builder()
            .id(10L)
            .eventType(AuthSessionOutboxEventType.SET_DEVICE_ACTIVE)
            .userId(USER_ID)
            .deviceUuid(DEVICE_UUID)
            .expiresAt(expiresAt)
            .build();

        when(outboxEventRepository.save(any(AuthSessionOutboxEvent.class))).thenReturn(savedEvent);
        when(outboxEventRepository.findById(10L)).thenReturn(Optional.of(savedEvent));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        service.enqueueSetDeviceActive(USER_ID, DEVICE_UUID, expiresAt);

        verify(outboxEventRepository).save(any(AuthSessionOutboxEvent.class));
        verify(valueOperations).set(eq(DEVICE_CACHE_KEY), eq("1"), any(Duration.class));
        verify(transactionManager).commit(any());
        assertThat(savedEvent.getStatus()).isEqualTo(AuthSessionOutboxStatus.DONE);
    }

    @Test
    @DisplayName("Redis 실패 시 이벤트를 FAILED로 남겨 재시도 대상이 되게 한다")
    void enqueueDeleteDeviceActive_marksFailedWhenRedisFails() {
        AuthSessionOutboxService service = newService();
        AuthSessionOutboxEvent savedEvent = AuthSessionOutboxEvent.builder()
            .id(11L)
            .eventType(AuthSessionOutboxEventType.DELETE_DEVICE_ACTIVE)
            .userId(USER_ID)
            .deviceUuid(DEVICE_UUID)
            .build();

        when(outboxEventRepository.save(any(AuthSessionOutboxEvent.class))).thenReturn(savedEvent);
        when(outboxEventRepository.findById(11L)).thenReturn(Optional.of(savedEvent));
        when(redisTemplate.delete(DEVICE_CACHE_KEY)).thenThrow(new IllegalStateException("redis down"));
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        service.enqueueDeleteDeviceActive(USER_ID, DEVICE_UUID);

        assertThat(savedEvent.getStatus()).isEqualTo(AuthSessionOutboxStatus.FAILED);
        assertThat(savedEvent.getRetryCount()).isEqualTo(1);
        verify(transactionManager).commit(any());
    }

    private AuthSessionOutboxService newService() {
        return new AuthSessionOutboxService(outboxEventRepository, redisTemplate, transactionManager);
    }
}
