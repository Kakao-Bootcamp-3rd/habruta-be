package com.imyme.mine.domain.auth.controller;

import com.imyme.mine.domain.auth.dto.*;
import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.service.KakaoRedirectUriResolver;
import com.imyme.mine.domain.auth.service.LogoutService;
import com.imyme.mine.domain.auth.service.OAuthService;
import com.imyme.mine.domain.auth.service.TokenRefreshService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "02. Auth", description = "OAuth 로그인, 토큰 관리, 로그아웃 API")
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OAuthService oauthService;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;
    private final KakaoRedirectUriResolver kakaoRedirectUriResolver;

    @Operation(
        summary = "카카오 OAuth Redirect URI 조회",
        description = "클라이언트의 Origin 헤더를 기반으로 환경(Local/Dev/Prod)에 맞는 Redirect URI를 반환합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Redirect URI 조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "등록되지 않은 Origin - INVALID_ORIGIN",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "알 수 없는 환경 - INVALID_ENVIRONMENT",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @GetMapping("/kakao/redirect-uri")
    public ApiResponse<KakaoRedirectUriResponse> getKakaoRedirectUri(
        @RequestHeader(value = "Origin", required = false) String origin
    ) {
        log.info("Kakao Redirect URI 요청 - Origin: {}", origin);

        String redirectUri = kakaoRedirectUriResolver.resolveRedirectUri(origin);

        return ApiResponse.success(
            new KakaoRedirectUriResponse(redirectUri),
            "Redirect URI 조회 성공"
        );
    }

    @Operation(
        summary = "OAuth 로그인",
        description = "카카오 OAuth를 통한 로그인 및 회원가입. 신규 사용자는 자동 회원가입되며 isNewUser 필드로 구분 가능합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "로그인 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - INVALID_OAUTH_CODE(유효하지 않은 인증 코드), INVALID_PROVIDER(지원하지 않는 제공자)",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "OAuth 제공자 서버 오류 - OAUTH_PROVIDER_ERROR",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping("/oauth/{provider}")
    public ApiResponse<OAuthLoginResponse> oauthLogin(
        @PathVariable String provider,
        @Valid @RequestBody OAuthLoginRequest request
    ) {
        log.info("OAuth login attempt: provider={}", provider);

        OAuthProviderType oauthProvider;
        try {
            oauthProvider = OAuthProviderType.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }

        if (oauthProvider != OAuthProviderType.KAKAO) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER);
        }

        OAuthLoginResponse response = oauthService.loginWithKakao(request);

        if (Boolean.TRUE.equals(response.user().isNewUser())) {
            return ApiResponse.success(response, "회원가입이 완료되었습니다.");
        } else {
            return ApiResponse.success(response, "로그인되었습니다.");
        }
    }

    @Operation(
        summary = "토큰 갱신",
        description = "Refresh Token을 사용하여 새로운 Access Token과 Refresh Token을 발급받습니다. Refresh Token Rotation 방식으로 보안을 강화합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "토큰 갱신 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "유효하지 않거나 만료된 Refresh Token - INVALID_REFRESH_TOKEN, REFRESH_TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "세션을 찾을 수 없음 - SESSION_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping("/refresh")
    public ApiResponse<TokenRefreshResponse> refreshToken(
        @Valid @RequestBody TokenRefreshRequest request
    ) {
        log.info("Token refresh attempt");

        TokenRefreshResponse response = tokenRefreshService.refreshTokens(request);

        return ApiResponse.success(response, "토큰이 갱신되었습니다.");
    }

    @Operation(
        summary = "로그아웃",
        description = "현재 기기에서 로그아웃합니다. UserSession을 삭제하고 Device를 Soft Delete 처리합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "로그아웃 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "세션을 찾을 수 없음 - SESSION_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
        @CurrentUser UserPrincipal userPrincipal,
        @Valid @RequestBody LogoutRequest request) {
        log.info("Logout attempt: userId={}, deviceUuid={}", userPrincipal.getId(), request.deviceUuid());

        logoutService.logout(userPrincipal.getId(), request.deviceUuid());

        log.info("Logout successful: userId={}, deviceUuid={}", userPrincipal.getId(), request.deviceUuid());
    }
}
