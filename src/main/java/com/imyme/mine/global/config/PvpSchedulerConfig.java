package com.imyme.mine.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄러 Bean 설정
 *
 * <p>두 개의 스케줄러를 분리하여 PvP 타이머가 전역 @Scheduled 잡 실행기에 영향을 주지 않도록 한다.
 * Spring의 ScheduledAnnotationBeanPostProcessor는 "taskScheduler" 이름의 Bean을 우선 탐색하므로,
 * pvpTimerScheduler 추가만으로는 기존 @Scheduled 잡 실행기가 바뀌지 않는다.
 */
@Configuration
public class PvpSchedulerConfig {

    /**
     * 전역 @Scheduled 잡 전용 스케줄러
     * - RetentionScheduler, AttemptExpirationScheduler 등이 사용
     * - 기존 Spring Boot 기본 동작과 동일하게 단일 스레드로 유지
     */
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * PvP 타이머 전용 스케줄러
     * - scheduleThinkingTransition / scheduleRecordingTransition / scheduleRecordingTimeout 에서만 사용
     * - removeOnCancelPolicy: 취소된 작업을 큐에서 즉시 제거하여 메모리 누수 방지
     */
    @Bean("pvpTimerScheduler")
    public ThreadPoolTaskScheduler pvpTimerScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("pvp-timer-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}