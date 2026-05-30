package com.imyme.mine.domain.card.scheduler;

import com.imyme.mine.domain.card.messaging.CardCreatedStreamPublisher;
import com.imyme.mine.domain.card.service.CardCountBatchService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardCreatedStreamConsumerScheduler {

    private static final String GROUP_NAME = "card-count-updaters";
    private static final int BATCH_SIZE = 500;
    private static final long STREAM_MAX_LENGTH = 100_000L;

    private final StringRedisTemplate redisTemplate;
    private final CardCountBatchService cardCountBatchService;

    @Value("${spring.application.name:mine}")
    private String applicationName;

    private String consumerName;

    @PostConstruct
    public void initializeConsumerGroup() {
        consumerName = applicationName + "-" + hostname();

        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.streamCommands().xGroupCreate(
                    CardCreatedStreamPublisher.STREAM_KEY.getBytes(StandardCharsets.UTF_8),
                    GROUP_NAME,
                    ReadOffset.from("0-0"),
                    true
                );
                return null;
            });
        } catch (RedisSystemException e) {
            if (!String.valueOf(e.getMessage()).contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    @Scheduled(fixedDelay = 500)
    public void consumeCardCreatedEvents() {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
            Consumer.from(GROUP_NAME, consumerName),
            StreamReadOptions.empty().count(BATCH_SIZE),
            StreamOffset.create(CardCreatedStreamPublisher.STREAM_KEY, ReadOffset.lastConsumed())
        );

        if (records == null || records.isEmpty()) {
            return;
        }

        Map<Long, Integer> deltas = aggregateDeltas(records);
        int updatedUsers = cardCountBatchService.applyDeltas(deltas);

        RecordId[] recordIds = records.stream()
            .map(MapRecord::getId)
            .toArray(RecordId[]::new);

        redisTemplate.opsForStream().acknowledge(CardCreatedStreamPublisher.STREAM_KEY, GROUP_NAME, recordIds);
        redisTemplate.opsForStream().trim(CardCreatedStreamPublisher.STREAM_KEY, STREAM_MAX_LENGTH, true);

        log.info("[CardCreatedStream] 카드 생성 이벤트 배치 반영 완료 - events={}, users={}",
            records.size(), updatedUsers);
    }

    private Map<Long, Integer> aggregateDeltas(List<MapRecord<String, Object, Object>> records) {
        Map<Long, Integer> deltas = new HashMap<>();
        for (MapRecord<String, Object, Object> record : records) {
            Object rawUserId = record.getValue().get("userId");
            if (rawUserId == null) {
                log.warn("[CardCreatedStream] userId 없는 이벤트 스킵 - streamId={}", record.getId());
                continue;
            }

            Long userId = Long.valueOf(String.valueOf(rawUserId));
            deltas.merge(userId, 1, Integer::sum);
        }
        return deltas;
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }
}
