package com.imyme.mine.domain.pvp.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PvpSessionManager 단위 테스트
 * - @SpringBootTest 없이 비Spring 구조 유지
 * - Testcontainers Redis 직접 주입 (로컬 수동 Redis 불필요)
 */
@DisplayName("PvpSessionManager 단위 테스트")
class PvpSessionManagerTest {

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private PvpSessionManager sessionManager;
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeAll
    static void startContainer() {
        REDIS.start();
    }

    @AfterAll
    static void stopContainer() {
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config =
            new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(serializer);
        redisTemplate.afterPropertiesSet();

        sessionManager = new PvpSessionManager(redisTemplate, objectMapper);

        // 테스트 전 관련 키 정리
        var keys = redisTemplate.keys("pvp:session:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        var roomKeys = redisTemplate.keys("pvp:room:*:sessions");
        if (roomKeys != null && !roomKeys.isEmpty()) {
            redisTemplate.delete(roomKeys);
        }
    }

    @Test
    @DisplayName("세션 등록 후 조회 가능")
    void addAndGetSession() {
        sessionManager.addSession("session-1", 100L, 1L);

        SessionInfo info = sessionManager.getSession("session-1");
        assertNotNull(info);
        assertEquals("session-1", info.sessionId());
        assertEquals(100L, info.roomId());
        assertEquals(1L, info.userId());
        assertNotNull(info.joinedAt());
    }

    @Test
    @DisplayName("세션 제거 후 조회 시 null")
    void removeSession() {
        sessionManager.addSession("session-1", 100L, 1L);

        SessionInfo removed = sessionManager.removeSession("session-1");
        assertNotNull(removed);
        assertEquals(1L, removed.userId());

        assertNull(sessionManager.getSession("session-1"));
    }

    @Test
    @DisplayName("null 파라미터로 addSession 호출 시 등록 안됨")
    void addSessionWithNull() {
        sessionManager.addSession(null, 100L, 1L);
        sessionManager.addSession("session-1", null, 1L);
        sessionManager.addSession("session-1", 100L, null);

        assertEquals(0, sessionManager.getSessionCount());
    }

    @Test
    @DisplayName("null sessionId로 removeSession 호출 시 null 반환")
    void removeSessionWithNull() {
        assertNull(sessionManager.removeSession(null));
    }

    @Test
    @DisplayName("존재하지 않는 세션 제거 시 null 반환")
    void removeNonExistentSession() {
        assertNull(sessionManager.removeSession("non-existent"));
    }

    @Test
    @DisplayName("세션 수 정확히 카운트")
    void sessionCount() {
        assertEquals(0, sessionManager.getSessionCount());

        sessionManager.addSession("s1", 1L, 1L);
        sessionManager.addSession("s2", 1L, 2L);
        sessionManager.addSession("s3", 2L, 3L);

        assertEquals(3, sessionManager.getSessionCount());

        sessionManager.removeSession("s2");
        assertEquals(2, sessionManager.getSessionCount());
    }

    @Test
    @DisplayName("getAllSessions는 읽기 전용")
    void getAllSessionsIsUnmodifiable() {
        sessionManager.addSession("s1", 1L, 1L);

        assertThrows(UnsupportedOperationException.class, () ->
                sessionManager.getAllSessions().put("s2", new SessionInfo("s2", 2L, 2L, null))
        );
    }
}
