package com.imyme.mine.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Redis 단독 Testcontainers 지원 클래스
 * - Redis만 필요한 @SpringBootTest 기반 테스트에서 상속
 * - PostgreSQL 없이 Redis만 필요한 경우 IntegrationTestSupport 대신 사용
 */
@Testcontainers
public abstract class RedisContainerSupport {

    @SuppressWarnings("resource")
    @org.testcontainers.junit.jupiter.Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
