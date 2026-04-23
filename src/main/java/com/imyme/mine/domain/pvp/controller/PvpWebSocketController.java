package com.imyme.mine.domain.pvp.controller;

import com.imyme.mine.domain.pvp.entity.PvpRoom;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.websocket.PvpSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Optional;

/**
 * PvP WebSocket 컨트롤러 (세션 등록/제거 전용, DB 쓰기·브로드캐스트 없음)
 *
 * - /app/pvp/{roomId}/register-session  → 참여자 확인(read-only) + 세션 등록
 * - /app/pvp/{roomId}/unregister-session → 세션 제거
 *
 * DB 쓰기와 브로드캐스트는 REST API(PvpRoomController) 또는 WebSocketEventListener에서 수행합니다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PvpWebSocketController {

    private final PvpSessionManager sessionManager;
    private final PvpRoomRepository pvpRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 세션 등록 (DB 변경 없음)
     *
     * 클라이언트는 REST로 방 참여/생성 완료 후 WS 연결하여 이 메서드를 호출합니다.
     * - 참여자 확인 (read-only) → 세션 등록
     */
    @MessageMapping("/pvp/{roomId}/register-session")
    public void registerSession(@DestinationVariable Long roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long userId = extractUserId(headerAccessor);

        if (userId == null) {
            log.warn("WS 세션 등록 실패: userId 추출 불가 - sessionId={}, roomId={}", sessionId, roomId);
            return;
        }

        // 방 조회 (read-only)
        Optional<PvpRoom> roomOpt = pvpRoomRepository.findByIdWithDetails(roomId);
        if (roomOpt.isEmpty()) {
            log.warn("WS 세션 등록 실패: 방 없음 - roomId={}, userId={}", roomId, userId);
            sendError(userId, "존재하지 않는 방입니다.");
            return;
        }

        PvpRoom room = roomOpt.get();

        // 종료된 방은 세션 등록 불가
        if (room.getStatus() == PvpRoomStatus.CANCELED ||
            room.getStatus() == PvpRoomStatus.FINISHED ||
            room.getStatus() == PvpRoomStatus.EXPIRED) {
            log.warn("WS 세션 등록 실패: 종료된 방 - roomId={}, userId={}, status={}", roomId, userId, room.getStatus());
            sendError(userId, "이미 종료된 방입니다.");
            return;
        }

        // 참여자 확인 (호스트 또는 게스트만 세션 등록 가능)
        if (!room.isParticipant(userId)) {
            log.warn("WS 세션 등록 실패: 참여자 아님 - roomId={}, userId={}", roomId, userId);
            sendError(userId, "해당 방의 참여자가 아닙니다.");
            return;
        }

        // 세션 등록 (DB 변경 없음, 브로드캐스트 없음)
        sessionManager.addSession(sessionId, roomId, userId);
        log.info("WS 세션 등록: userId={}, roomId={}, role={}", userId, roomId, room.isHost(userId) ? "HOST" : "GUEST");
    }

    /**
     * 세션 제거 (DB 변경 없음)
     *
     * WS에서는 세션만 정리합니다. 실제 방 나가기는 REST DELETE /pvp/rooms/{roomId}로 처리합니다.
     */
    @MessageMapping("/pvp/{roomId}/unregister-session")
    public void unregisterSession(@DestinationVariable Long roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Long userId = extractUserId(headerAccessor);

        sessionManager.removeSession(sessionId);
        log.info("WS 세션 제거: userId={}, roomId={}, sessionId={}", userId, roomId, sessionId);
    }

    /**
     * 특정 유저에게 에러 메시지 전송
     * - 클라이언트 구독 경로: /user/queue/pvp/errors
     */
    private void sendError(Long userId, String errorMessage) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/pvp/errors",
                Map.of("error", errorMessage, "timestamp", System.currentTimeMillis())
        );
    }

    /**
     * sessionAttributes에서 userId 추출
     */
    private Long extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) {
            log.warn("sessionAttributes가 null입니다.");
            return null;
        }

        Object userIdObj = attrs.get("userId");
        if (userIdObj == null) {
            log.warn("sessionAttributes에 userId가 없습니다.");
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