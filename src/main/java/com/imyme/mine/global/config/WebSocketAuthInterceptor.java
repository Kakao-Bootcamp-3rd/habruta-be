package com.imyme.mine.global.config;

import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 핸드셰이크 시 JWT 인증
 * - Authorization 헤더 또는 쿼리 파라미터(token)에서 JWT 추출
 * - 유효한 토큰이면 userId를 WebSocket 세션 속성에 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        try {
            String token = extractToken(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                attributes.put("userId", userId);
                log.info("WebSocket handshake authenticated: userId={}", userId);
                return true;
            }

            log.warn("WebSocket handshake failed: invalid or missing token");
            return false;

        } catch (Exception e) {
            log.error("WebSocket handshake error", e);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // No-op
    }

    /**
     * Authorization 헤더 또는 쿼리 파라미터에서 토큰 추출
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. Authorization 헤더에서 추출
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. 쿼리 파라미터에서 추출 (모바일 클라이언트가 헤더를 보낼 수 없는 경우)
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String tokenParam = servletRequest.getServletRequest().getParameter("token");
            if (tokenParam != null) {
                return tokenParam;
            }
        }

        return null;
    }
}