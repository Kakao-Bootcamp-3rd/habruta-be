package com.imyme.mine.domain.learning.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.global.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Solo SSE 브로드캐스트 Redis 구독자
 * - 채널: solo:result:* 구독
 * - PUSH: SseEmitterRegistry.push() 호출 (연결 유지)
 * - EMIT: SseEmitterRegistry.emit() 호출 (연결 종료)
 * - 멀티 인스턴스 환경에서도 모든 인스턴스가 수신하므로 SSE 누락 없음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoloRedisSubscriber implements MessageListener {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SoloRedisMessage soloMessage = objectMapper.readValue(message.getBody(), SoloRedisMessage.class);

            Long attemptId = soloMessage.getAttemptId();
            log.debug("[Solo Redis] 메시지 수신: attemptId={}, type={}, status={}",
                attemptId, soloMessage.getType(), soloMessage.getStatus());

            if (soloMessage.getType() == SoloRedisMessage.Type.PUSH) {
                Map<String, Object> data = soloMessage.getStep() != null
                    ? Map.of("status", soloMessage.getStatus(), "step", soloMessage.getStep())
                    : Map.of("status", soloMessage.getStatus());
                sseEmitterRegistry.push(attemptId, data);
            } else {
                sseEmitterRegistry.emit(attemptId, soloMessage.getStatus());
            }
        } catch (Exception e) {
            log.error("[Solo Redis] 메시지 처리 실패", e);
        }
    }
}