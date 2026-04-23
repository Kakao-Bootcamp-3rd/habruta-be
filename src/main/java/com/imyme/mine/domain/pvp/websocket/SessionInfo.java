package com.imyme.mine.domain.pvp.websocket;

import java.time.LocalDateTime;

/**
 * WebSocket 세션 정보
 * - sessionId: STOMP 세션 ID
 * - roomId: 참여 중인 PvP 방 ID
 * - userId: 인증된 사용자 ID
 * - joinedAt: 세션 참여 시각
 */
public record SessionInfo(
        String sessionId,
        Long roomId,
        Long userId,
        LocalDateTime joinedAt
) {
}