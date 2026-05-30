package com.imyme.mine.domain.card.messaging;

import com.imyme.mine.domain.card.service.CardCountBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardCreatedStreamPublisher {

    public static final String STREAM_KEY = "card:created";

    private final StringRedisTemplate redisTemplate;
    private final CardCountBatchService cardCountBatchService;

    public void publishAfterCommit(Long userId, Long cardId) {
        Runnable publish = () -> publishOrFallback(userId, cardId);

        if (!TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isActualTransactionActive()) {
            publish.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    private void publishOrFallback(Long userId, Long cardId) {
        try {
            RecordId recordId = redisTemplate.opsForStream().add(STREAM_KEY, Map.of(
                "userId", String.valueOf(userId),
                "cardId", String.valueOf(cardId),
                "createdAt", Instant.now().toString()
            ));
            log.debug("[CardCreatedStream] 카드 생성 이벤트 발행 - streamId={}, userId={}, cardId={}",
                recordId, userId, cardId);
        } catch (RuntimeException e) {
            log.error("[CardCreatedStream] Redis Stream 발행 실패, DB 즉시 반영으로 대체 - userId={}, cardId={}",
                userId, cardId, e);
            cardCountBatchService.applyDeltas(Map.of(userId, 1));
        }
    }
}
