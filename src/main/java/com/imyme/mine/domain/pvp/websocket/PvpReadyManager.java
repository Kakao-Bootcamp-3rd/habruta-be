package com.imyme.mine.domain.pvp.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * PvP READY 상태 관리 (Redis 기반)
 * - 키: pvp:room:{roomId}:ready
 * - 타입: Set<userId>
 * - TTL: 15분 (방 수명과 맞춤)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PvpReadyManager {

    private static final String READY_KEY_PREFIX = "pvp:room:";
    private static final String READY_KEY_SUFFIX = ":ready";
    private static final Duration READY_TTL = Duration.ofMinutes(15);

    private final RedisTemplate<String, Object> redisTemplate;

    private String readyKey(Long roomId) {
        return READY_KEY_PREFIX + roomId + READY_KEY_SUFFIX;
    }

    /**
     * READY 등록 (SADD)
     * @return true if newly added, false if already exists
     */
    public boolean addReady(Long roomId, Long userId) {
        Long added = redisTemplate.opsForSet().add(readyKey(roomId), userId.toString());
        redisTemplate.expire(readyKey(roomId), READY_TTL);
        boolean isNew = added != null && added > 0;
        log.info("READY 등록: roomId={}, userId={}, isNew={}", roomId, userId, isNew);
        return isNew;
    }

    /**
     * READY 인원 수 조회 (SCARD)
     */
    public long getReadyCount(Long roomId) {
        Long size = redisTemplate.opsForSet().size(readyKey(roomId));
        return size != null ? size : 0;
    }

    /**
     * READY set 삭제 (DEL)
     */
    public void clearReady(Long roomId) {
        redisTemplate.delete(readyKey(roomId));
        log.info("READY 삭제: roomId={}", roomId);
    }
}