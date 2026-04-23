package com.imyme.mine.global.config;

import com.imyme.mine.domain.pvp.websocket.StompPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Map;

/**
 * WebSocket 설정 (STOMP 프로토콜)
 * - /ws 엔드포인트로 WebSocket 연결
 * - SockJS 폴백 지원
 * - 메시지 브로커 설정: /app (클라이언트→서버), /topic (브로드캐스트), /user (개인)
 * - STOMP CONNECT 시 Principal 자동 주입 → convertAndSendToUser() 지원
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]))
                .addInterceptors(webSocketAuthInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트 → 서버로 보내는 메시지 prefix
        registry.setApplicationDestinationPrefixes("/app");

        // 서버 → 클라이언트 브로드캐스트 prefix
        // /topic: 여러 사용자에게 전송 (게임 룸 전체)
        // /user: 특정 사용자에게만 전송
        registry.enableSimpleBroker("/topic", "/user");

        // /user 프리픽스를 사용할 때 사용자별 큐 설정
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * STOMP CONNECT 시 핸드셰이크에서 인증된 userId를 Principal로 주입
     * - WebSocketAuthInterceptor가 sessionAttributes에 넣어둔 userId를 꺼내서 StompPrincipal로 변환
     * - 이후 convertAndSendToUser()로 특정 유저에게 개인 메시지 전송 가능
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    Map<String, Object> attrs = accessor.getSessionAttributes();
                    if (attrs == null || attrs.get("userId") == null) {
                        throw new org.springframework.messaging.MessageDeliveryException(
                                "STOMP CONNECT 거부: 인증 정보 없음");
                    }
                    accessor.setUser(new StompPrincipal(attrs.get("userId").toString()));
                }
                return message;
            }
        });
    }
}