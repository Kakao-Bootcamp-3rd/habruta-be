package com.imyme.mine.domain.learning.service;

import com.imyme.mine.domain.ai.client.AiServerClient;
import com.imyme.mine.domain.learning.dto.WarmupResponse;
import com.imyme.mine.global.config.AiServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * STT 워밍업 서비스
 * - GPU 콜드 스타트 방지
 * - 쿨다운 기간 내 중복 AI 서버 호출 방지 (불필요한 부하 감소)
 * - 사용자에게는 항상 성공 응답 반환 (UX 개선)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarmupService {

    private final AiServerClient aiServerClient;
    private final AiServerProperties aiServerProperties;

    // 전역 "마지막 AI 서버 Warm-up 호출 시간" 추적 (모든 사용자 공유)
    private final AtomicReference<LocalDateTime> lastAiServerWarmupTime = new AtomicReference<>();

    /**
     * STT 워밍업 요청
     * - 쿨다운 기간 내면 AI 서버 호출 생략 (이미 Warm 상태로 간주)
     * - 쿨다운 기간 외면 비동기로 AI 서버 Warm-up 호출
     * - 사용자에게는 항상 202 Accepted 응답 반환
     */
    public WarmupResponse warmup(Long userId) {
        log.debug("STT 워밍업 요청 - userId: {}", userId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastWarmup = lastAiServerWarmupTime.get();

        // 쿨다운 체크: 최근에 Warm-up 했으면 AI 서버 호출 생략
        if (lastWarmup != null &&
            lastWarmup.plusSeconds(aiServerProperties.getWarmupCooldownSeconds()).isAfter(now)) {
            long secondsSinceLastWarmup = java.time.Duration.between(lastWarmup, now).getSeconds();
            log.info("쿨다운 기간 내 Warm-up 요청 - AI 서버 호출 생략 - userId: {}, 마지막 Warm-up: {}초 전",
                     userId, secondsSinceLastWarmup);
            return WarmupResponse.warmingUp();
        }

        // 쿨다운 기간 외: AI 서버에 실제 Warm-up 호출
        lastAiServerWarmupTime.set(now);

        // CompletableFuture로 비동기 워밍업 호출
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                log.debug("비동기 워밍업 실행 - userId: {}", userId);
                aiServerClient.warmup();
                log.info("비동기 워밍업 완료 - userId: {}", userId);
            } catch (Exception e) {
                // 워밍업 실패는 치명적이지 않으므로 로그만 기록
                log.error("비동기 워밍업 실패 - userId: {}, error: {}", userId, e.getMessage());
            }
        });

        log.info("STT 워밍업 시작 (AI 서버 호출) - userId: {}", userId);
        return WarmupResponse.warmingUp();
    }
}