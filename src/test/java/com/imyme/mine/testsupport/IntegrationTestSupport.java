package com.imyme.mine.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL + Redis Testcontainers 통합 지원 클래스
 * - @SpringBootTest 기반 통합 테스트에서 상속
 * - NicknameService(@PostConstruct + 런타임)와 OAuthService 로그인 흐름이 Redis에 의존하므로
 *   인증 관련 @SpringBootTest는 Postgres만으로 컨텍스트 로딩 불가
 */
@Testcontainers
public abstract class IntegrationTestSupport {

    @org.testcontainers.junit.jupiter.Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15")
                .asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    @org.testcontainers.junit.jupiter.Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
