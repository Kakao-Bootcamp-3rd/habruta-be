package com.imyme.mine.domain.pvp.dto.websocket;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 방 상태 변경 알림 (브로드캐스트)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomStatusChangeMessage {
    private PvpRoomStatus status;
    private KeywordData keyword;
    private LocalDateTime startedAt;
    private LocalDateTime thinkingEndsAt;
    private String message;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeywordData {
        private Long id;
        private String name;
    }
}
