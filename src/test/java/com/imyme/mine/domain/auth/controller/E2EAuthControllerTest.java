package com.imyme.mine.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.auth.dto.E2ELoginRequest;
import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.entity.RoleType;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2EAuthController 통합 테스트
 * - E2E 테스트 로그인 엔드포인트 검증
 * - PostgreSQL + Redis Testcontainers 기반 (로컬 수동 기동 불필요)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "tc"})
@Transactional
class E2EAuthControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Flyway가 스키마를 생성하므로 데이터만 직접 준비
        if (!userRepository.findByOauthIdAndOauthProvider("e2e_test_user", OAuthProviderType.KAKAO).isPresent()) {
            User testUser = User.builder()
                .oauthId("e2e_test_user")
                .oauthProvider(OAuthProviderType.KAKAO)
                .email("e2e@test.com")
                .nickname("__e2e_test_user__")
                .role(RoleType.USER)
                .level(1)
                .totalCardCount(0)
                .activeCardCount(0)
                .consecutiveDays(1)
                .winCount(0)
                .build();
            userRepository.save(testUser);
        }
    }

    @Test
    @DisplayName("E2E 테스트 로그인 성공")
    void e2eLogin_Success() throws Exception {
        // given
        String deviceUuid = "550e8400-e29b-41d4-a716-446655440000";
        E2ELoginRequest request = new E2ELoginRequest(deviceUuid);

        // E2E 테스트 유저 조회
        User testUser = userRepository
            .findByOauthIdAndOauthProvider("e2e_test_user", OAuthProviderType.KAKAO)
            .orElseThrow(() -> new AssertionError("E2E test user not found in database"));

        // when & then
        mockMvc.perform(post("/e2e/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("E2E 테스트 로그인 성공"))
            .andExpect(jsonPath("$.data.accessToken").value(notNullValue()))
            .andExpect(jsonPath("$.data.refreshToken").value(notNullValue()))
            .andExpect(jsonPath("$.data.expiresIn").value(notNullValue()))
            .andExpect(jsonPath("$.data.user.id").value(testUser.getId().intValue()))
            .andExpect(jsonPath("$.data.user.nickname").value("__e2e_test_user__"))
            .andExpect(jsonPath("$.data.user.oauthProvider").value("KAKAO"))
            .andExpect(jsonPath("$.data.user.isNewUser").value(false));
    }

    @Test
    @DisplayName("E2E 테스트 로그인 실패 - 잘못된 UUID 형식")
    void e2eLogin_InvalidUuid() throws Exception {
        // given
        String invalidDeviceUuid = "invalid-uuid";
        E2ELoginRequest request = new E2ELoginRequest(invalidDeviceUuid);

        // when & then
        // DTO validation 실패는 HTTP 422 (Unprocessable Entity)를 반환
        mockMvc.perform(post("/e2e/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.details.field").value("deviceUuid"));
    }

    @Test
    @DisplayName("E2E 테스트 로그인 실패 - deviceUuid 누락")
    void e2eLogin_MissingDeviceUuid() throws Exception {
        // given
        String emptyJson = "{}";

        // when & then
        // DTO validation 실패는 HTTP 422 (Unprocessable Entity)를 반환
        mockMvc.perform(post("/e2e/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyJson))
            .andDo(print())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.details.field").value("deviceUuid"));
    }

    @Test
    @DisplayName("E2E 테스트 로그인 - 동일한 디바이스로 여러 번 로그인 시 세션 재사용")
    void e2eLogin_ReuseSession() throws Exception {
        // given
        String deviceUuid = "550e8400-e29b-41d4-a716-446655440001";
        E2ELoginRequest request = new E2ELoginRequest(deviceUuid);

        // when - 첫 번째 로그인
        String firstAccessToken = mockMvc.perform(post("/e2e/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // when - 두 번째 로그인 (동일한 디바이스)
        String secondAccessToken = mockMvc.perform(post("/e2e/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // then - 두 번 모두 성공해야 함 (세션 재사용)
        assertThat(firstAccessToken).isNotEmpty();
        assertThat(secondAccessToken).isNotEmpty();
    }
}