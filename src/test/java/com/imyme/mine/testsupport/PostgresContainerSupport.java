package com.imyme.mine.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL Testcontainers 공통 지원 클래스
 * - pgvector 확장이 포함된 이미지 사용 (V20260129_0001 migration 필수 조건)
 * - pg15 고정: docker-compose.yml의 postgres:15-alpine과 major 버전 일치
 */
@Testcontainers
public abstract class PostgresContainerSupport {

    @org.testcontainers.junit.jupiter.Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15")
                .asCompatibleSubstituteFor("postgres")  // 호환 이미지 명시 필수
        )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}