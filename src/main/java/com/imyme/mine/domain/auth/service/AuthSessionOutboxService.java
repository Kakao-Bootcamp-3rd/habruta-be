package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.entity.AuthSessionOutboxEvent;
import com.imyme.mine.domain.auth.entity.AuthSessionOutboxEventType;
import com.imyme.mine.domain.auth.entity.AuthSessionOutboxStatus;
import com.imyme.mine.domain.auth.repository.AuthSessionOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionOutboxService {

    private static final String ACTIVE_SESSION_KEY_PREFIX = "auth:session:active:";
    private static final int MAX_RETRIES = 10;
    private static final int BATCH_SIZE = 50;

    private final AuthSessionOutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final PlatformTransactionManager transactionManager;

    public void enqueueSetUserActive(Long userId, LocalDateTime expiresAt) {
        enqueue(AuthSessionOutboxEventType.SET_USER_ACTIVE, userId, null, expiresAt);
    }

    public void enqueueSetDeviceActive(Long userId, String deviceUuid, LocalDateTime expiresAt) {
        enqueue(AuthSessionOutboxEventType.SET_DEVICE_ACTIVE, userId, deviceUuid, expiresAt);
    }

    public void enqueueDeleteUserActive(Long userId) {
        enqueue(AuthSessionOutboxEventType.DELETE_USER_ACTIVE, userId, null, null);
    }

    public void enqueueDeleteDeviceActive(Long userId, String deviceUuid) {
        enqueue(AuthSessionOutboxEventType.DELETE_DEVICE_ACTIVE, userId, deviceUuid, null);
    }

    @Transactional
    public int processDueEvents() {
        List<AuthSessionOutboxEvent> events = outboxEventRepository.findProcessableEvents(
            List.of(AuthSessionOutboxStatus.PENDING, AuthSessionOutboxStatus.FAILED),
            MAX_RETRIES,
            LocalDateTime.now(),
            PageRequest.of(0, BATCH_SIZE)
        );

        events.forEach(this::processEvent);
        return events.size();
    }

    public void processEventNow(Long eventId) {
        new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> outboxEventRepository.findById(eventId).ifPresent(this::processEvent));
    }

    private void enqueue(AuthSessionOutboxEventType type, Long userId, String deviceUuid, LocalDateTime expiresAt) {
        AuthSessionOutboxEvent event = outboxEventRepository.save(AuthSessionOutboxEvent.builder()
            .eventType(type)
            .userId(userId)
            .deviceUuid(deviceUuid)
            .expiresAt(expiresAt)
            .build());

        runAfterCommit(() -> processEventNow(event.getId()));
    }

    private void processEvent(AuthSessionOutboxEvent event) {
        if (event.getStatus() == AuthSessionOutboxStatus.DONE || event.getStatus() == AuthSessionOutboxStatus.DEAD) {
            return;
        }

        try {
            applyRedisOperation(event);
            event.markDone();
        } catch (RuntimeException e) {
            event.markFailed(e.getMessage(), MAX_RETRIES);
            log.warn(
                "Auth session outbox Redis 작업 실패: eventId={}, type={}, userId={}, deviceUuid={}, retryCount={}",
                event.getId(),
                event.getEventType(),
                event.getUserId(),
                event.getDeviceUuid(),
                event.getRetryCount(),
                e
            );
        }
    }

    private void applyRedisOperation(AuthSessionOutboxEvent event) {
        switch (event.getEventType()) {
            case SET_USER_ACTIVE -> setActive(activeSessionKey(event.getUserId()), event.getExpiresAt());
            case SET_DEVICE_ACTIVE -> setActive(activeSessionKey(event.getUserId(), event.getDeviceUuid()), event.getExpiresAt());
            case DELETE_USER_ACTIVE -> redisTemplate.delete(activeSessionKey(event.getUserId()));
            case DELETE_DEVICE_ACTIVE -> redisTemplate.delete(activeSessionKey(event.getUserId(), event.getDeviceUuid()));
        }
    }

    private void setActive(String key, LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required for Redis SET auth session event");
        }

        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            redisTemplate.delete(key);
            return;
        }

        redisTemplate.opsForValue().set(key, "1", ttl);
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String activeSessionKey(Long userId) {
        return ACTIVE_SESSION_KEY_PREFIX + userId;
    }

    private String activeSessionKey(Long userId, String deviceUuid) {
        return ACTIVE_SESSION_KEY_PREFIX + userId + ":" + deviceUuid;
    }
}
