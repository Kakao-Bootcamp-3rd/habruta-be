package com.imyme.mine.domain.pvp.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PvP WebSocket 세션 관리자 (Redis 기반)
 * - sessionId → SessionInfo 매핑: pvp:session:{sessionId}
 * - roomId → sessionId Set 역매핑: pvp:room:{roomId}:sessions
 * - TTL 2시간: 비정상 종료 시 자동 정리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PvpSessionManager {

    private static final String SESSION_KEY_PREFIX = "pvp:session:";
    private static final String ROOM_KEY_PREFIX = "pvp:room:";
    private static final String ROOM_KEY_SUFFIX = ":sessions";
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String roomKey(Long roomId) {
        return ROOM_KEY_PREFIX + roomId + ROOM_KEY_SUFFIX;
    }

    /**
     * 세션 등록
     */
    public void addSession(String sessionId, Long roomId, Long userId) {
        if (sessionId == null || roomId == null || userId == null) {
            log.warn("addSession 실패: null 파라미터 - sessionId={}, roomId={}, userId={}",
                    sessionId, roomId, userId);
            return;
        }

        SessionInfo info = new SessionInfo(sessionId, roomId, userId, LocalDateTime.now());
        redisTemplate.opsForValue().set(sessionKey(sessionId), info, SESSION_TTL);
        redisTemplate.opsForSet().add(roomKey(roomId), sessionId);
        redisTemplate.expire(roomKey(roomId), SESSION_TTL);
        log.info("세션 등록: sessionId={}, roomId={}, userId={}", sessionId, roomId, userId);
    }

    /**
     * 세션 제거
     */
    public SessionInfo removeSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        SessionInfo removed = getSession(sessionId);
        if (removed != null) {
            redisTemplate.delete(sessionKey(sessionId));
            String roomKeyStr = roomKey(removed.roomId());
            redisTemplate.opsForSet().remove(roomKeyStr, sessionId);

            // room set이 비었으면 key 삭제 (메모리 정리)
            Long remainingSize = redisTemplate.opsForSet().size(roomKeyStr);
            if (remainingSize != null && remainingSize == 0) {
                redisTemplate.delete(roomKeyStr);
            }

            log.info("세션 제거: sessionId={}, roomId={}, userId={}",
                    sessionId, removed.roomId(), removed.userId());
        }
        return removed;
    }

    /**
     * 세션 ID로 SessionInfo 조회
     */
    public SessionInfo getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(sessionKey(sessionId));
        return convertToSessionInfo(value);
    }

    /**
     * 방에 연결된 세션 수 조회
     */
    public int getRoomSessionCount(Long roomId) {
        if (roomId == null) {
            return 0;
        }
        Long size = redisTemplate.opsForSet().size(roomKey(roomId));
        return size == null ? 0 : size.intValue();
    }

    /**
     * 방에 연결된 세션 목록 조회
     */
    public List<SessionInfo> getSessionsByRoom(Long roomId) {
        if (roomId == null) {
            return Collections.emptyList();
        }
        Set<Object> sessionIds = redisTemplate.opsForSet().members(roomKey(roomId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sessionIds.stream()
                .map(sid -> getSession(sid.toString()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 같은 방에 같은 유저의 다른 세션이 있는지 확인 (멀티탭 방어)
     */
    public boolean hasOtherSessionsForUser(Long roomId, Long userId, String excludeSessionId) {
        Set<Object> sessionIds = redisTemplate.opsForSet().members(roomKey(roomId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return false;
        }
        return sessionIds.stream()
                .map(Object::toString)
                .filter(sid -> !sid.equals(excludeSessionId))
                .map(this::getSession)
                .filter(Objects::nonNull)
                .anyMatch(info -> info.userId().equals(userId));
    }

    /**
     * 전체 세션 수 조회
     */
    public int getSessionCount() {
        Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
        return keys == null ? 0 : keys.size();
    }

    /**
     * 전체 세션 목록 조회 (읽기 전용)
     */
    public Map<String, SessionInfo> getAllSessions() {
        Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SessionInfo> result = new LinkedHashMap<>();
        for (String key : keys) {
            String sessionId = key.substring(SESSION_KEY_PREFIX.length());
            SessionInfo info = getSession(sessionId);
            if (info != null) {
                result.put(sessionId, info);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Redis에서 조회한 Object를 SessionInfo로 변환
     * - RedisTemplate value serializer가 Object 타입이라 LinkedHashMap으로 반환될 수 있음
     */
    private SessionInfo convertToSessionInfo(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof SessionInfo info) {
            return info;
        }
        try {
            return objectMapper.convertValue(value, SessionInfo.class);
        } catch (Exception e) {
            log.warn("SessionInfo 변환 실패: {}", e.getMessage());
            return null;
        }
    }
}
