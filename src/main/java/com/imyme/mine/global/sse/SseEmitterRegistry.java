package com.imyme.mine.global.sse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE Emitter 레지스트리
 * - attemptId → SseEmitter 매핑 관리
 * - 30초 간격 Heartbeat로 프록시 연결 유지
 * - Virtual Thread에서 emit() 호출 시 스레드 안전 보장 (ConcurrentHashMap)
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    /** SseEmitter 타임아웃: 솔로 폴링 최대 대기(180s) + 여유 5s */
    private static final long SSE_TIMEOUT_MS = 185_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });

    @PostConstruct
    public void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(
            this::sendHeartbeats,
            HEARTBEAT_INTERVAL_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        log.info("[SSE] Heartbeat 스케줄러 시작 (간격={}ms)", HEARTBEAT_INTERVAL_MS);
    }

    /**
     * Emitter 등록
     * - onCompletion / onTimeout / onError 시 자동 정리
     */
    public SseEmitter register(Long attemptId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Runnable cleanup = () -> {
            emitters.remove(attemptId, emitter);  // 재구독 시 새 emitter를 지우지 않도록 atomic compare-and-remove
            log.info("[SSE] emitter 제거: attemptId={}, 남은 수={}", attemptId, emitters.size());
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        emitters.put(attemptId, emitter);
        log.info("[SSE] emitter 등록: attemptId={}, 총 등록 수={}", attemptId, emitters.size());
        return emitter;
    }

    /**
     * 중간 단계 이벤트 전송 (연결 유지)
     * - AUDIO_ANALYSIS → FEEDBACK_GENERATION 전환 시 사용
     * - emit()과 달리 emitter를 종료하지 않음
     */
    public void push(Long attemptId, Map<String, Object> data) {
        SseEmitter emitter = emitters.get(attemptId);  // remove 하지 않음
        if (emitter == null) {
            log.debug("[SSE] push 대상 없음 (미연결 or 이미 종료): attemptId={}", attemptId);
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("status-update").data(data));
            log.debug("[SSE] 중간 이벤트 전송: attemptId={}, data={}", attemptId, data);
        } catch (Exception e) {
            log.debug("[SSE] push 실패 (연결 종료 추정): attemptId={}", attemptId);
            emitters.remove(attemptId);
        }
    }

    /**
     * 분석 완료/실패 시 클라이언트에 Push
     * - Virtual Thread에서 호출
     * - emitter가 없으면 (미연결 or 이미 종료) no-op
     */
    public void emit(Long attemptId, String status) {
        SseEmitter emitter = emitters.remove(attemptId);
        if (emitter == null) {
            log.debug("[SSE] 전송 대상 없음 (미연결 or 이미 종료): attemptId={}", attemptId);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                .name("status-update")
                .data(Map.of("status", status)));
            emitter.complete();
            log.info("[SSE] 이벤트 전송 완료: attemptId={}, status={}", attemptId, status);
        } catch (IOException e) {
            log.warn("[SSE] 이벤트 전송 실패: attemptId={}", attemptId, e);
            emitter.completeWithError(e);
        }
    }

    private void sendHeartbeats() {
        if (emitters.isEmpty()) return;
        emitters.forEach((attemptId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                log.debug("[SSE] heartbeat 실패 (연결 종료 추정): attemptId={}", attemptId);
                emitters.remove(attemptId, emitter);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdown();
    }
}