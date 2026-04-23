package com.imyme.mine.domain.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * WebSocket 테스트용 컨트롤러
 * - 클라이언트가 /app/test/echo로 메시지를 보내면
 * - /topic/test/echo를 구독한 모든 클라이언트에게 브로드캐스트
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TestWebSocketController {

    /**
     * WebSocket Echo 테스트
     * 클라이언트 → /app/test/echo
     * 서버 → /topic/test/echo (브로드캐스트)
     */
    @MessageMapping("/test/echo")
    @SendTo("/topic/test/echo")
    public String echo(@Payload String message, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = extractUserId(headerAccessor);
        log.info("WebSocket echo: userId={}, message={}", userId, message);
        return String.format("[Echo from userId=%d] %s", userId, message);
    }

    /**
     * sessionAttributes에서 userId 추출 (null 방어)
     */
    private Long extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) {
            log.warn("sessionAttributes가 null입니다.");
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