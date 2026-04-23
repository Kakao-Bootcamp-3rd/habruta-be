package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 프로필 이미지 설정
 * - application.yml에서 값 주입
 * - Presigned URL 만료 시간 관리
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "profile-image")
public class ProfileImageProperties {

    /**
     * Presigned URL 생성 시간 (분)
     * - 프로필 이미지 업로드용 Presigned URL 만료 시간
     * - 기본값: 5분
     */
    private int presignedUrlExpirationMinutes = 5;

    /**
     * 이미지 조회 URL 만료 시간 (시간)
     * - 프로필 이미지 조회용 Presigned GET URL 만료 시간
     * - 기본값: 1시간
     */
    private int getUrlExpirationHours = 1;
}