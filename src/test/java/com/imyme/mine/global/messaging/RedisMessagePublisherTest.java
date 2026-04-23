package com.imyme.mine.global.messaging;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.messaging.PvpChannels;
import com.imyme.mine.domain.pvp.messaging.PvpMessage;
import com.imyme.mine.testsupport.IntegrationTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Pub/Sub 통합 테스트
 * - Testcontainers 기반 (로컬 수동 Redis 불필요)
 */
@Slf4j
@SpringBootTest
@ActiveProfiles({"test", "tc"})
class RedisMessagePublisherTest extends IntegrationTestSupport {

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private RedisMessageListenerContainer listenerContainer;

    /**
     * 메시지 발행/구독 테스트
     * - 메시지를 발행하고 구독자가 수신하는지 확인
     */
    @Test
    void testPublishAndSubscribe() throws InterruptedException {
        // Given
        Long roomId = 1L;
        String channel = PvpChannels.getRoomChannel(roomId);
        PvpMessage message = PvpMessage.statusChange(roomId, PvpRoomStatus.MATCHED, "테스트 메시지");

        CountDownLatch latch = new CountDownLatch(1);
        String[] receivedMessage = new String[1];

        // 구독자 등록
        MessageListener listener = (Message msg, byte[] pattern) -> {
            receivedMessage[0] = new String(msg.getBody());
            log.info("메시지 수신: {}", receivedMessage[0]);
            latch.countDown();
        };

        listenerContainer.addMessageListener(listener, new ChannelTopic(channel));

        // When - 메시지 발행
        messagePublisher.publish(channel, message);

        // Then - 1초 내에 메시지 수신 확인
        boolean received = latch.await(1, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedMessage[0]).contains("MATCHED");
        assertThat(receivedMessage[0]).contains("테스트 메시지");

        log.info("✅ 테스트 성공: 메시지 발행/구독 정상 동작");
    }

    /**
     * 여러 채널 격리 테스트
     * - 방 1번 채널에 발행한 메시지가 방 2번 채널에 수신되지 않는지 확인
     */
    @Test
    void testChannelIsolation() throws InterruptedException {
        // Given
        String channel1 = PvpChannels.getRoomChannel(1L);
        String channel2 = PvpChannels.getRoomChannel(2L);

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        // 방 1번 구독자
        MessageListener listener1 = (msg, pattern) -> latch1.countDown();
        listenerContainer.addMessageListener(listener1, new ChannelTopic(channel1));

        // 방 2번 구독자
        MessageListener listener2 = (msg, pattern) -> latch2.countDown();
        listenerContainer.addMessageListener(listener2, new ChannelTopic(channel2));

        // When - 방 1번에만 메시지 발행
        PvpMessage message = PvpMessage.statusChange(1L, PvpRoomStatus.MATCHED, "방 1번 메시지");
        messagePublisher.publish(channel1, message);

        // Then
        boolean received1 = latch1.await(1, TimeUnit.SECONDS);
        boolean received2 = latch2.await(1, TimeUnit.SECONDS);

        assertThat(received1).isTrue();  // 방 1번은 수신
        assertThat(received2).isFalse(); // 방 2번은 수신 안함

        log.info("✅ 테스트 성공: 채널 격리 정상 동작");
    }

    /**
     * 다양한 메시지 타입 테스트
     */
    @Test
    void testVariousMessageTypes() throws InterruptedException {
        // Given
        Long roomId = 999L;
        String channel = PvpChannels.getRoomChannel(roomId);
        CountDownLatch latch = new CountDownLatch(3);

        listenerContainer.addMessageListener(
                (msg, pattern) -> {
                    log.info("메시지 수신: {}", new String(msg.getBody()));
                    latch.countDown();
                },
                new ChannelTopic(channel)
        );

        // When - 다양한 메시지 발행
        messagePublisher.publish(channel, PvpMessage.statusChange(roomId, PvpRoomStatus.MATCHED, "매칭 완료"));
        messagePublisher.publish(channel, PvpMessage.guestJoined(roomId, "게스트 정보", "GUEST"));
        messagePublisher.publish(channel, PvpMessage.recordingStarted(roomId));

        // Then
        boolean allReceived = latch.await(2, TimeUnit.SECONDS);
        assertThat(allReceived).isTrue();

        log.info("✅ 테스트 성공: 다양한 메시지 타입 발행/구독 정상 동작");
    }
}