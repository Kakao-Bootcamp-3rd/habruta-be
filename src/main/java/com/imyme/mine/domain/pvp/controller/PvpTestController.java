package com.imyme.mine.domain.pvp.controller;

import com.imyme.mine.domain.pvp.dto.MessageType;
import com.imyme.mine.domain.pvp.dto.websocket.PvpWebSocketMessage;
import com.imyme.mine.domain.pvp.dto.websocket.RoomStatusChangeMessage;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.websocket.PvpSessionManager;
import com.imyme.mine.domain.pvp.websocket.SessionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * PvP WebSocket 테스트용 REST 컨트롤러
 * - 브라우저/Postman에서 WebSocket 브로드캐스트를 수동으로 트리거
 * - 운영 환경에서는 제거 또는 비활성화
 */
@Slf4j
@RestController
@RequestMapping("/test/pvp")
@RequiredArgsConstructor
public class PvpTestController {

    private final SimpMessagingTemplate messagingTemplate;
    private final PvpSessionManager sessionManager;

    /**
     * 수동으로 STATUS_CHANGE 브로드캐스트 트리거
     */
    @PostMapping("/rooms/{roomId}/status-change")
    public Map<String, Object> triggerStatusChange(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "THINKING") String status
    ) {
        PvpRoomStatus roomStatus;
        try {
            roomStatus = PvpRoomStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return Map.of("success", false, "error", "유효하지 않은 상태: " + status);
        }

        RoomStatusChangeMessage statusData = RoomStatusChangeMessage.builder()
                .status(roomStatus)
                .message("테스트 상태 변경: " + roomStatus)
                .build();

        messagingTemplate.convertAndSend(
                "/topic/pvp/" + roomId,
                PvpWebSocketMessage.of(MessageType.STATUS_CHANGE, roomId, statusData)
        );

        log.info("테스트 STATUS_CHANGE 전송: roomId={}, status={}", roomId, roomStatus);
        return Map.of("success", true, "roomId", roomId, "status", roomStatus.name());
    }

    /**
     * 현재 세션 수 조회
     */
    @GetMapping("/sessions/count")
    public Map<String, Object> getSessionCount() {
        return Map.of("count", sessionManager.getSessionCount());
    }

    /**
     * 전체 세션 목록 조회
     */
    @GetMapping("/sessions")
    public Map<String, SessionInfo> getAllSessions() {
        return sessionManager.getAllSessions();
    }

    /**
     * WebSocket 테스트 페이지 (add-mappings: false 환경에서 직접 서빙)
     */
    @GetMapping(value = "/page", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String testPage() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/websocket-test.html");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}