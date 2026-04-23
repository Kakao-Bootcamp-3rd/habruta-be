package com.imyme.mine.domain.pvp.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 종료 알림
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameEndMessage {
    private WinnerData winner;
    private String message;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WinnerData {
        private Long userId;
        private String nickname;
        private Integer score;
    }
}
