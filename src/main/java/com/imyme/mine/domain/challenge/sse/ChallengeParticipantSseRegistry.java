package com.imyme.mine.domain.challenge.sse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 챌린지 참여자 수 SSE Emitter 레지스트리
 *
 * <p>challengeId → List&lt;SseEmitter&gt; 1:N 구조.
 * 클라이언트는 챌린지 OPEN 시 구독하고 upload-complete 이벤트마다 count를 수신.
 * 챌린지 게이트 종료(CLOSED) 시 {@link #closeAll}로 전체 정리.
 */
@Slf4j
@Component
public class ChallengeParticipantSseRegistry {

    private static final long SSE_TIMEOUT_MS     = 60 * 60 * 1000L; // 60분 (챌린지 OPEN 최대 시간)
    private static final long HEARTBEAT_INTERVAL = 30_000L;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "challenge-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    public void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(
                this::sendHeartbeats,
                HEARTBEAT_INTERVAL,
                HEARTBEAT_INTERVAL,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 새 SseEmitter 등록 및 반환
     */
    public SseEmitter subscribe(Long challengeId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list =
                emitters.computeIfAbsent(challengeId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable cleanup = () -> {
            list.remove(emitter);
            log.debug("[Challenge SSE] emitter 제거: challengeId={}, 남은 수={}", challengeId, list.size());
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.debug("[Challenge SSE] emitter 등록: challengeId={}, 총={}", challengeId, list.size());
        return emitter;
    }

    /**
     * 전체 구독자에게 count 이벤트 push.
     * 전송 실패한 emitter는 즉시 제거.
     */
    public void broadcast(Long challengeId, int count) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(challengeId);
        if (list == null || list.isEmpty()) return;

        Map<String, Object> data = Map.of("count", count);
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("count").data(data));
            } catch (Exception e) {
                log.debug("[Challenge SSE] broadcast 실패 (연결 종료 추정): challengeId={}", challengeId);
                list.remove(emitter);
            }
        }
        log.debug("[Challenge SSE] broadcast 완료: challengeId={}, count={}, 구독자={}", challengeId, count, list.size());
    }

    /**
     * 챌린지 종료(CLOSED) 시 전체 emitter 정리
     */
    public void closeAll(Long challengeId) {
        List<SseEmitter> list = emitters.remove(challengeId);
        if (list == null) return;
        list.forEach(SseEmitter::complete);
        log.info("[Challenge SSE] 전체 종료: challengeId={}, 종료 수={}", challengeId, list.size());
    }

    private void sendHeartbeats() {
        emitters.forEach((challengeId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception e) {
                    list.remove(emitter);
                }
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdown();
    }
}
