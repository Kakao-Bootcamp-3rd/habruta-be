package com.imyme.mine.domain.pvp.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상대방 답변 제출 알림
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerSubmittedMessage {
    private Long userId;
    private String nickname;
    private String role;
    private String message;
}
