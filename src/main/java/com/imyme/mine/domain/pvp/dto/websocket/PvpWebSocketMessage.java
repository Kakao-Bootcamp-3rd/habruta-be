package com.imyme.mine.domain.pvp.dto.websocket;

import com.imyme.mine.domain.pvp.dto.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PvP WebSocket 기본 메시지 래퍼
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PvpWebSocketMessage<T> {
    private MessageType type;
    private Long roomId;
    private T data;
    private String message;
    private Long timestamp;

    public static <T> PvpWebSocketMessage<T> of(MessageType type, Long roomId, T data) {
        return PvpWebSocketMessage.<T>builder()
                .type(type)
                .roomId(roomId)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> PvpWebSocketMessage<T> error(Long roomId, String message) {
        return PvpWebSocketMessage.<T>builder()
                .type(MessageType.ERROR)
                .roomId(roomId)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}