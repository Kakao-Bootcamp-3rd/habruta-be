package com.imyme.mine.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 카카오 OAuth Redirect URI 응답
 */
@Schema(description = "카카오 OAuth Redirect URI 응답")
public record KakaoRedirectUriResponse(
    @Schema(description = "환경에 맞는 Redirect URI", example = "http://localhost:8080/oauth/kakao/callback")
    String redirectUri
) {}