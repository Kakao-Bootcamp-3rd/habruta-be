package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionCacheService {

    private static final String ACTIVE_SESSION_KEY_PREFIX = "auth:session:active:";

    private final StringRedisTemplate redisTemplate;
    private final UserSessionRepository userSessionRepository;

    public boolean hasActiveSession(Long userId) {
        String key = activeSessionKey(userId);

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("Redis 세션 캐시 조회 실패 - DB fallback 진행: userId={}", userId, e);
        }

        return userSessionRepository.findMaxExpiresAtByUserId(userId)
            .map(expiresAt -> {
                if (!expiresAt.isAfter(LocalDateTime.now())) {
                    evictActiveSession(userId);
                    return false;
                }

                cacheActiveSession(userId, expiresAt);
                return true;
            })
            .orElse(false);
    }

    public void markActiveAfterCommit(Long userId, LocalDateTime expiresAt) {
        runAfterCommit(() -> cacheActiveSession(userId, expiresAt));
    }

    public void refreshAfterCommit(Long userId) {
        runAfterCommit(() -> userSessionRepository.findMaxExpiresAtByUserId(userId)
            .ifPresentOrElse(
                expiresAt -> cacheActiveSession(userId, expiresAt),
                () -> evictActiveSession(userId)
            ));
    }

    public void evictAfterCommit(Long userId) {
        runAfterCommit(() -> evictActiveSession(userId));
    }

    private void cacheActiveSession(Long userId, LocalDateTime expiresAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            evictActiveSession(userId);
            return;
        }

        try {
            redisTemplate.opsForValue().set(activeSessionKey(userId), "1", ttl);
        } catch (RuntimeException e) {
            log.warn("Redis 세션 캐시 저장 실패: userId={}", userId, e);
        }
    }

    private void evictActiveSession(Long userId) {
        try {
            redisTemplate.delete(activeSessionKey(userId));
        } catch (RuntimeException e) {
            log.warn("Redis 세션 캐시 삭제 실패: userId={}", userId, e);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
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
}
