package com.imyme.mine.acceptance;

import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * 인증 핵심 시나리오 인수테스트
 * - 실제 HTTP 호출 기반 (MockMvc 아님)
 * - PostgreSQL + Redis Testcontainers 기반
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles({"test", "tc"})
class AuthAcceptanceTest extends IntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @DisplayName("E2E 로그인 성공 - accessToken과 refreshToken 발급")
    void e2eLogin_success() {
        // given
        String url = "http://localhost:" + port + "/e2e/login";
        Map<String, String> request = Map.of("deviceUuid", "550e8400-e29b-41d4-a716-446655440000");

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);

        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data.get("accessToken")).isNotNull();
        assertThat(data.get("refreshToken")).isNotNull();
        assertThat(data.get("expiresIn")).isNotNull();
    }

    @Test
    @DisplayName("E2E 로그인 실패 - 잘못된 UUID 형식은 422 반환")
    void e2eLogin_invalidUuid_returns422() {
        // given
        String url = "http://localhost:" + port + "/e2e/login";
        Map<String, String> request = Map.of("deviceUuid", "invalid-uuid");

        // when
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("VALIDATION_FAILED");

        Map<?, ?> details = (Map<?, ?>) body.get("details");
        assertThat(details.get("field")).isEqualTo("deviceUuid");
    }

    @Test
    @DisplayName("동일 디바이스로 두 번 로그인 - 모두 성공 (세션 재사용 정책)")
    void e2eLogin_sameDevice_bothSucceed() throws InterruptedException {
        // given
        String url = "http://localhost:" + port + "/e2e/login";
        Map<String, String> request = Map.of("deviceUuid", "550e8400-e29b-41d4-a716-446655440001");

        // when
        Thread.sleep(1100); // 이전 테스트와 초(second)가 달라지도록 대기 (JWT는 초 단위 정밀도)
        ResponseEntity<Map> first = restTemplate.postForEntity(url, request, Map.class);
        Thread.sleep(1100); // 동일 초 내 JWT 중복 생성 방지
        ResponseEntity<Map> second = restTemplate.postForEntity(url, request, Map.class);

        // then
        assertThat(first.getStatusCode())
            .as("first call failed: " + first.getBody())
            .isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode())
            .as("second call failed: " + second.getBody())
            .isEqualTo(HttpStatus.OK);

        Map<?, ?> firstData = (Map<?, ?>) first.getBody().get("data");
        Map<?, ?> secondData = (Map<?, ?>) second.getBody().get("data");
        assertThat(firstData.get("accessToken")).isNotNull();
        assertThat(secondData.get("accessToken")).isNotNull();
    }
}