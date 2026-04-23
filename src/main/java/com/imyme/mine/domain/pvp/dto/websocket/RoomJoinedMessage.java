package com.imyme.mine.domain.pvp.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게스트 입장 알림 (브로드캐스트)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomJoinedMessage {
    private Long userId;
    private String nickname;
    private String role;
    private String message;
}