package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 카카오 OAuth 동적 Redirect URI 설정
 * - 환경별 (local/dev/prod) Redirect URI 관리
 * - Origin 기반 환경 판단을 위한 매핑
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "oauth.kakao")
public class OAuthKakaoProperties {

    /**
     * 환경별 Redirect URI
     * - local: 로컬 개발 환경
     * - dev: 개발 서버 환경
     * - prod: 운영 서버 환경
     */
    private RedirectUris redirectUris;

    /**
     * Origin 환경 매핑
     * - local, dev, prod 각각에 대한 Origin URL
     */
    private Origins origins;

    @Getter
    @Setter
    public static class RedirectUris {
        private String local;
        private String dev;
        private String prod;
    }

    @Getter
    @Setter
    public static class Origins {
        private String local;
        private String dev;
        private String prod;
    }
}