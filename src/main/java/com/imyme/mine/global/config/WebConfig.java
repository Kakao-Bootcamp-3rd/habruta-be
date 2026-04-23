package com.imyme.mine.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * Web 설정
 * - RestTemplate Bean 등록
 * - CORS 설정은 SecurityConfig에서 관리
 * - 비동기 처리 활성화
 */
@Configuration
@EnableAsync
public class WebConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}