package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * CORS 설정
 * - .env 파일에서 CORS_ALLOWED_ORIGINS 환경변수로 관리
 * - 쉼표로 구분된 Origin 목록을 List로 변환
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    /**
     * 허용할 출처 목록
     */
    private List<String> allowedOrigins;
}
