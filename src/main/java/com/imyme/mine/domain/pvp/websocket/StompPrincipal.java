package com.imyme.mine.domain.pvp.websocket;

import java.security.Principal;

/**
 * STOMP Principal 구현
 * - WebSocket 핸드셰이크에서 인증된 userId를 Principal로 변환
 * - convertAndSendToUser() 사용을 위해 필요
 */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}