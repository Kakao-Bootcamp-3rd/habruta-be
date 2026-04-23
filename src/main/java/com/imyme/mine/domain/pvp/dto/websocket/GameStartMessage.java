package com.imyme.mine.domain.pvp.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게임 시작 알림
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStartMessage {
    private Long keywordId;
    private String keywordName;
    private LocalDateTime startedAt;
    private Integer thinkingSeconds;
    private String message;
}
