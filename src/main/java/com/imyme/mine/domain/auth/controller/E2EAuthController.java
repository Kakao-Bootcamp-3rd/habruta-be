package com.imyme.mine.domain.auth.controller;

import com.imyme.mine.domain.auth.dto.E2ELoginRequest;
import com.imyme.mine.domain.auth.dto.OAuthLoginResponse;
import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.service.OAuthService;
import com.imyme.mine.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E2E 테스트 전용 인증 컨트롤러
 * - 운영 환경을 제외한 모든 환경에서 활성화됩니다 (@Profile("!prod"))
 * - Swagger 문서에 노출되지 않습니다 (@Hidden)
 * - 실제 OAuth 프로세스를 거치지 않고 고정된 테스트 계정으로 로그인할 수 있습니다.
 */
@Hidden
// 운영(prod) 환경을 제외한 모든 환경에서 활성화 (로컬, dev, test 등)
@Profile("!prod")
@Slf4j
@RestController
@RequestMapping("/e2e")
@RequiredArgsConstructor
public class E2EAuthController {

    private static final String E2E_TEST_USER_OAUTH_ID = "e2e_test_user";
    private static final String E2E_SOCKET_HOST_OAUTH_ID = "e2e_socket_host";
    private static final String E2E_SOCKET_GUEST_OAUTH_ID = "e2e_socket_guest";

    private final UserRepository userRepository;
    private final OAuthService oauthService;

    /**
     * E2E 테스트 유저 생성 (또는 조회)
     * - E2E 테스트 유저가 없으면 생성하고, 있으면 기존 유저 정보를 반환합니다.
     * - 한 번만 실행하면 됩니다.
     */
    @PostMapping("/create-user")
    @Transactional
    public ApiResponse<User> createE2EUser() {
        log.info("E2E test user creation attempt");

        User testUser = userRepository
            .findByOauthIdAndOauthProvider(E2E_TEST_USER_OAUTH_ID, OAuthProviderType.KAKAO)
            .orElseGet(() -> {
                User newUser = User.builder()
                    .oauthId(E2E_TEST_USER_OAUTH_ID)
                    .oauthProvider(OAuthProviderType.KAKAO)
                    .nickname("__e2e_test_user__")
                    .build();

                userRepository.save(newUser);
                log.info("E2E test user created: userId={}", newUser.getId());
                return newUser;
            });

        return ApiResponse.success(testUser, "E2E 테스트 유저 준비 완료");
    }

    /**
     * E2E 테스트 로그인 엔드포인트
     * - 사전에 정의된 테스트 계정으로 즉시 로그인합니다.
     * - OAuth 인증 과정 없이 Access Token과 Refresh Token을 발급합니다.
     *
     * @param request deviceUuid를 포함한 로그인 요청
     * @return Access Token과 Refresh Token을 포함한 로그인 응답
     */
    @PostMapping("/login")
    @Transactional
    public ApiResponse<OAuthLoginResponse> e2eLogin(@Valid @RequestBody E2ELoginRequest request) {
        log.info("E2E test login attempt: deviceUuid={}", request.deviceUuid());

        // E2E 테스트 유저 조회 (없으면 자동 생성)
        User testUser = userRepository
            .findByOauthIdAndOauthProvider(E2E_TEST_USER_OAUTH_ID, OAuthProviderType.KAKAO)
            .orElseGet(() -> {
                User newUser = User.builder()
                    .oauthId(E2E_TEST_USER_OAUTH_ID)
                    .oauthProvider(OAuthProviderType.KAKAO)
                    .nickname("__e2e_test_user__")
                    .build();

                userRepository.save(newUser);
                log.info("E2E test user auto-created during login: userId={}", newUser.getId());
                return newUser;
            });

        // 공통 로그인 로직 실행 (토큰 발급)
        OAuthLoginResponse response = oauthService.login(testUser, request.deviceUuid(), false);

        log.info("E2E test login successful: userId={}, deviceUuid={}", testUser.getId(), request.deviceUuid());

        return ApiResponse.success(response, "E2E 테스트 로그인 성공");
    }

    /**
     * E2E 소켓 테스트 HOST 로그인
     * - PvP 소켓 테스트에서 방을 생성하는 호스트 역할
     */
    @PostMapping("/login/host")
    @Transactional
    public ApiResponse<OAuthLoginResponse> e2eHostLogin(@Valid @RequestBody E2ELoginRequest request) {
        log.info("E2E socket host login attempt: deviceUuid={}", request.deviceUuid());

        User hostUser = userRepository
            .findByOauthIdAndOauthProvider(E2E_SOCKET_HOST_OAUTH_ID, OAuthProviderType.KAKAO)
            .orElseGet(() -> {
                User newUser = User.builder()
                    .oauthId(E2E_SOCKET_HOST_OAUTH_ID)
                    .oauthProvider(OAuthProviderType.KAKAO)
                    .nickname("__e2e_socket_host__")
                    .build();
                userRepository.save(newUser);
                log.info("E2E socket host auto-created: userId={}", newUser.getId());
                return newUser;
            });

        OAuthLoginResponse response = oauthService.login(hostUser, request.deviceUuid(), false);
        log.info("E2E socket host login successful: userId={}", hostUser.getId());

        return ApiResponse.success(response, "E2E 소켓 호스트 로그인 성공");
    }

    /**
     * E2E 소켓 테스트 GUEST 로그인
     * - PvP 소켓 테스트에서 방에 입장하는 게스트 역할
     */
    @PostMapping("/login/guest")
    @Transactional
    public ApiResponse<OAuthLoginResponse> e2eGuestLogin(@Valid @RequestBody E2ELoginRequest request) {
        log.info("E2E socket guest login attempt: deviceUuid={}", request.deviceUuid());

        User guestUser = userRepository
            .findByOauthIdAndOauthProvider(E2E_SOCKET_GUEST_OAUTH_ID, OAuthProviderType.KAKAO)
            .orElseGet(() -> {
                User newUser = User.builder()
                    .oauthId(E2E_SOCKET_GUEST_OAUTH_ID)
                    .oauthProvider(OAuthProviderType.KAKAO)
                    .nickname("__e2e_socket_guest__")
                    .build();
                userRepository.save(newUser);
                log.info("E2E socket guest auto-created: userId={}", newUser.getId());
                return newUser;
            });

        OAuthLoginResponse response = oauthService.login(guestUser, request.deviceUuid(), false);
        log.info("E2E socket guest login successful: userId={}", guestUser.getId());

        return ApiResponse.success(response, "E2E 소켓 게스트 로그인 성공");
    }

    /**
     * 부하 테스트용 VU별 로그인
     * - 각 VU(Virtual User)가 다른 유저로 로그인합니다.
     * - vuId에 따라 e2e_load_test_user_1, e2e_load_test_user_2, ... 유저가 생성됩니다.
     */
    @PostMapping("/login/{vuId}")
    @Transactional
    public ApiResponse<OAuthLoginResponse> e2eLoadTestLogin(
        @PathVariable int vuId,
        @Valid @RequestBody E2ELoginRequest request
    ) {
        String oauthId = "e2e_load_test_user_" + vuId;
        log.info("E2E load test login attempt: vuId={}, deviceUuid={}", vuId, request.deviceUuid());

        User testUser = userRepository
            .findByOauthIdAndOauthProvider(oauthId, OAuthProviderType.KAKAO)
            .orElseGet(() -> {
                User newUser = User.builder()
                    .oauthId(oauthId)
                    .oauthProvider(OAuthProviderType.KAKAO)
                    .nickname("부하테스터" + vuId)
                    .build();
                userRepository.save(newUser);
                log.info("E2E load test user auto-created: vuId={}, userId={}", vuId, newUser.getId());
                return newUser;
            });

        OAuthLoginResponse response = oauthService.login(testUser, request.deviceUuid(), false);
        log.info("E2E load test login successful: vuId={}, userId={}", vuId, testUser.getId());

        return ApiResponse.success(response, "E2E 부하 테스트 로그인 성공");
    }
}
