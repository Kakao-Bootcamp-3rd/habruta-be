package com.imyme.mine.domain.pvp.websocket;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.messaging.PvpChannels;
import com.imyme.mine.domain.pvp.messaging.PvpMessage;
import com.imyme.mine.domain.pvp.service.PvpRoomService;
import com.imyme.mine.domain.pvp.service.PvpRoomService.LeaveResult;
import com.imyme.mine.domain.pvp.service.PvpRoomService.LeaveType;
import com.imyme.mine.global.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

/**
 * WebSocket 이벤트 리스너
 * - CONNECT: 로깅
 * - DISCONNECT: 세션 정리 + DB 업데이트 + 상대방에게 broadcast
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PvpSessionManager sessionManager;
    private final PvpRoomService pvpRoomService;
    private final MessagePublisher messagePublisher;

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        Long userId = extractUserId(headerAccessor);
        log.info("WebSocket CONNECT: sessionId={}, userId={}", sessionId, userId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // 세션 매니저에서 제거
        SessionInfo removed = sessionManager.removeSession(sessionId);

        if (removed == null) {
            log.info("WebSocket DISCONNECT: sessionId={} - 등록된 세션 없음 (방 참여 전 종료)", sessionId);
            return;
        }

        Long roomId = removed.roomId();
        Long userId = removed.userId();

        log.info("WebSocket DISCONNECT: sessionId={}, roomId={}, userId={} - 세션 정리 완료",
                sessionId, roomId, userId);

        // 멀티탭 방어: 같은 유저의 다른 세션이 남아있으면 DB 정리 스킵
        if (sessionManager.hasOtherSessionsForUser(roomId, userId, sessionId)) {
            log.info("멀티탭 감지: 다른 세션 존재 - roomId={}, userId={}, DB 정리 스킵", roomId, userId);
            return;
        }

        // Service에 DB 정리 위임
        LeaveResult result = pvpRoomService.handleDisconnect(userId, roomId);

        if (result == null) {
            return;
        }

        // disconnect 브로드캐스트
        broadcastDisconnect(result, roomId, userId);
    }

    private void broadcastDisconnect(LeaveResult result, Long roomId, Long userId) {
        if (result.type() == LeaveType.HOST_LEFT) {
            messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                    PvpMessage.hostLeft(roomId));
        } else {
            messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                    PvpMessage.guestLeft(roomId, userId, "GUEST"));
            messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                    PvpMessage.statusChange(roomId, PvpRoomStatus.OPEN, "대결 상대를 기다리고 있습니다."));
        }
    }

    /**
     * sessionAttributes에서 userId 추출 (null 방어)
     */
    private Long extractUserId(StompHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) {
            return null;
        }

        Object userIdObj = attrs.get("userId");
        if (userIdObj == null) {
            return null;
        }

        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }

        log.warn("userId 타입 불일치: type={}, value={}", userIdObj.getClass().getName(), userIdObj);
        return null;
    }
}