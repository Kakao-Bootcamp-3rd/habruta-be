package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 카카오 OAuth 설정
 * - application.yml에서 값 주입
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration.kakao")
public class KakaoOAuthProperties {

    // REST API 키 (Client ID)
    private String clientId;

    // Client Secret (보안 키)
    private String clientSecret;

    // Redirect URI
    private String redirectUri;

    // 카카오 토큰 발급 URL
    private String tokenUri = "https://kauth.kakao.com/oauth/token";

    // 카카오 사용자 정보 조회 URL
    private String userInfoUri = "https://kapi.kakao.com/v2/user/me";
}
