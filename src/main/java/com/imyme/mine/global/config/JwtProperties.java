package com.imyme.mine.global.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 설정
 * - application.yml에서 값 주입
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    // JWT 서명용 Secret Key (최소 256bit = 32자 이상)
    private String secret;

    // Access Token 만료 시간 (밀리초) : 기본값 3600000 (1시간)
    private Long accessTokenExpiration = 3600000L;

    // Refresh Token 만료 시간 (밀리초) : 기본값: 604800000 (7일)
    private Long refreshTokenExpiration = 604800000L;

    // JWT Secret 검증 : 최소 32자 이상 (256bit) / 기본값 또는 예측 가능한 값 사용 방지
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT secret must be configured. Set jwt.secret in application.yml");
        }

        // 최소 길이 검증 (256bit = 32자)
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters long (256 bits). Current length: "
                            + secret.length());
        }

        // 기본값 사용 검증 (프로덕션 환경에서 위험)
        String[] unsafeDefaults = {
            "your-256-bit-secret",
            "your-secret-key",
            "change-me",
            "default-secret",
            "secret",
            "password",
            "12345"
        };

        String lowerSecret = secret.toLowerCase();
        for (String unsafeDefault : unsafeDefaults) {
            if (lowerSecret.contains(unsafeDefault)) {
                log.error(
                        "⚠️  SECURITY WARNING: JWT secret contains unsafe default value: '{}'",
                        unsafeDefault);
                log.error(
                        "⚠️  Please change jwt.secret in application.yml to a strong random value!");
                log.error(
                        "⚠️  Generate a secure secret: openssl rand -base64 32 or use UUID generator");

                // 프로덕션 환경에서는 예외 발생
                String profile = System.getProperty("spring.profiles.active", "");
                if (profile.contains("prod") || profile.contains("production")) {
                    throw new IllegalStateException(
                            "Unsafe JWT secret detected in production environment. Change jwt.secret immediately!");
                }
            }
        }

        log.info("JWT secret validated successfully (length: {} characters)", secret.length());
    }
}
