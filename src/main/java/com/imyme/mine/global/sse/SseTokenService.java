package com.imyme.mine.global.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * SSE One-time Token 서비스
 * - EventSource API는 Authorization 헤더 미지원 → Redis 기반 1회용 단기 토큰으로 인증 우회
 * - 토큰 발급: POST /stream-token (JWT 인증) → Redis SET with TTL
 * - 토큰 소비: GET /stream (token 쿼리 파라미터) → getAndDelete → 검증 완료 후 즉시 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseTokenService {

    private static final String TOKEN_KEY_PREFIX = "sse:token:";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(30);

    private static final String FIELD_ATTEMPT_ID = "attemptId";
    private static final String FIELD_USER_ID = "userId";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * SSE 스트림 접속용 1회용 토큰 발급
     *
     * @param attemptId 시도 ID
     * @param userId    요청자 userId (소유권 확인용으로 함께 저장)
     * @return 30초 유효한 1회용 UUID 토큰
     */
    public String issueToken(Long attemptId, Long userId) {
        String token = UUID.randomUUID().toString();
        String key = TOKEN_KEY_PREFIX + token;

        Map<String, Object> payload = Map.of(
            FIELD_ATTEMPT_ID, attemptId,
            FIELD_USER_ID, userId
        );

        redisTemplate.opsForValue().set(key, payload, TOKEN_TTL);
        log.debug("[SSE] 토큰 발급: attemptId={}, userId={}, token={}", attemptId, userId, token);
        return token;
    }

    /**
     * 토큰 검증 및 소비 (원자적 getAndDelete)
     * - 한 번 소비된 토큰은 재사용 불가
     * - TTL 만료된 토큰도 null 반환
     *
     * @param token 클라이언트가 전달한 토큰
     * @return {attemptId, userId} 맵, 유효하지 않으면 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> validateAndConsume(String token) {
        String key = TOKEN_KEY_PREFIX + token;
        Object value = redisTemplate.opsForValue().getAndDelete(key);

        if (value == null) {
            log.debug("[SSE] 토큰 검증 실패 (만료 or 미존재): token={}", token);
            return null;
        }

        log.debug("[SSE] 토큰 소비 완료: token={}", token);
        return (Map<String, Object>) value;
    }
}