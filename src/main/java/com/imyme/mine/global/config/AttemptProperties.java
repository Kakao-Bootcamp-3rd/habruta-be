package com.imyme.mine.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 학습 시도 설정
 * - application.yml에서 값 주입
 * - 업로드 및 Presigned URL 만료 시간 관리
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "attempt")
public class AttemptProperties {

    /**
     * 업로드 만료 시간 (분)
     * - 시도 생성 후 오디오 업로드 가능 시간
     * - 기본값: 10분
     */
    private int uploadExpirationMinutes = 10;

    /**
     * Presigned URL 만료 시간 (시간)
     * - AI 서버가 S3에서 오디오 다운로드 가능 시간
     * - 기본값: 1시간
     */
    private int presignedUrlExpirationHours = 1;
}