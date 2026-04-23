package com.imyme.mine.domain.pvp.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.pvp.dto.MessageType;
import com.imyme.mine.domain.pvp.dto.websocket.AnswerSubmittedMessage;
import com.imyme.mine.domain.pvp.dto.websocket.PvpWebSocketMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomJoinedMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomStatusChangeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Redis Pub/Sub 구독자
 * - Redis 채널(pvp:room:*)에서 PvpMessage를 수신
 * - PvpWebSocketMessage로 변환하여 STOMP 클라이언트에 전달
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PvpRedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. RedisTemplate serializer로 역직렬화 (byte[] → Object)
            Object raw = redisTemplate.getValueSerializer().deserialize(message.getBody());
            // 2. ObjectMapper로 PvpMessage 변환 (LinkedHashMap → PvpMessage)
            PvpMessage pvpMessage = objectMapper.convertValue(raw, PvpMessage.class);

            Long roomId = pvpMessage.getRoomId();
            String destination = "/topic/pvp/" + roomId;

            // 3. PvpMessageType → STOMP 메시지 변환 후 전달
            PvpWebSocketMessage<?> wsMessage = toWebSocketMessage(pvpMessage);
            if (wsMessage != null) {
                messagingTemplate.convertAndSend(destination, wsMessage);
                log.debug("STOMP 전달 완료: type={}, roomId={}", pvpMessage.getType(), roomId);
            }
        } catch (Exception e) {
            log.error("Redis 메시지 처리 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private PvpWebSocketMessage<?> toWebSocketMessage(PvpMessage pvpMessage) {
        Long roomId = pvpMessage.getRoomId();
        Map<String, Object> data = pvpMessage.getData() instanceof Map
                ? (Map<String, Object>) pvpMessage.getData() : null;

        return switch (pvpMessage.getType()) {
            case GUEST_JOINED -> {
                RoomJoinedMessage joined = RoomJoinedMessage.builder()
                        .userId(data != null ? toLong(data.get("userId")) : null)
                        .nickname(data != null ? (String) data.get("nickname") : null)
                        .role(data != null ? (String) data.get("role") : null)
                        .message(pvpMessage.getMessage())
                        .build();
                yield PvpWebSocketMessage.of(MessageType.ROOM_JOINED, roomId, joined);
            }
            case GUEST_LEFT -> {
                RoomJoinedMessage left = RoomJoinedMessage.builder()
                        .userId(data != null ? toLong(data.get("userId")) : null)
                        .role(data != null ? (String) data.get("role") : null)
                        .message(pvpMessage.getMessage())
                        .build();
                yield PvpWebSocketMessage.of(MessageType.ROOM_LEFT, roomId, left);
            }
            case HOST_LEFT -> {
                RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                        .status(pvpMessage.getStatus())
                        .message(pvpMessage.getMessage())
                        .build();
                yield PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData);
            }
            case STATUS_CHANGE -> {
                RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                        .status(pvpMessage.getStatus())
                        .message(pvpMessage.getMessage())
                        .build();
                yield PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData);
            }
            case THINKING_STARTED -> {
                RoomStatusChangeMessage.RoomStatusChangeMessageBuilder builder = RoomStatusChangeMessage.builder()
                        .status(pvpMessage.getStatus())
                        .message(pvpMessage.getMessage());

                if (data != null) {
                    builder.keyword(RoomStatusChangeMessage.KeywordData.builder()
                            .id(toLong(data.get("keywordId")))
                            .name((String) data.get("keywordName"))
                            .build());
                    String startedAtStr = (String) data.get("startedAt");
                    String thinkingEndsAtStr = (String) data.get("thinkingEndsAt");
                    if (startedAtStr != null) builder.startedAt(LocalDateTime.parse(startedAtStr));
                    if (thinkingEndsAtStr != null) builder.thinkingEndsAt(LocalDateTime.parse(thinkingEndsAtStr));
                }

                yield PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, builder.build());
            }
            case RECORDING_STARTED -> {
                RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                        .status(pvpMessage.getStatus())
                        .message(pvpMessage.getMessage())
                        .build();
                yield PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData);
            }
            case READY -> {
                RoomJoinedMessage readyData = RoomJoinedMessage.builder()
                        .userId(data != null ? toLong(data.get("userId")) : null)
                        .nickname(data != null ? (String) data.get("nickname") : null)
                        .role(data != null ? (String) data.get("role") : null)
                        .message(pvpMessage.getMessage())
                        .build();
                yield PvpWebSocketMessage.of(MessageType.PLAYER_READY, roomId, readyData);
            }
            case ANSWER_SUBMITTED -> {
                AnswerSubmittedMessage submitted = AnswerSubmittedMessage.builder()
                        .userId(data != null ? toLong(data.get("userId")) : null)
                        .nickname(data != null ? (String) data.get("nickname") : null)
                        .role(data != null ? (String) data.get("role") : null)
                        .message(pvpMessage.getMessage())
                        .build();
                yield PvpWebSocketMessage.of(MessageType.ANSWER_SUBMITTED, roomId, submitted);
            }
            case ANALYSIS_COMPLETED -> {
                RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                        .status(pvpMessage.getStatus())
                        .message(pvpMessage.getMessage())
                        .build();
                yield PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData);
            }
            default -> {
                log.warn("알 수 없는 PvpMessageType: {}", pvpMessage.getType());
                yield null;
            }
        };
    }

    private Long toLong(Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return null;
    }
}
