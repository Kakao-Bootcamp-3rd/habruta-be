package com.imyme.mine.domain.pvp.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * STT 변환 응답 DTO (RabbitMQ 메시지)
 * AI Server → Main Server
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SttResponseDto implements Serializable {

    /**
     * 요청 고유 ID (pass-through)
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 방 ID
     */
    @JsonProperty("room_id")
    private Long roomId;

    /**
     * 사용자 ID
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * 상태 (SUCCESS, FAIL)
     */
    @JsonProperty("status")
    private String status;

    /**
     * STT 변환된 텍스트
     */
    @JsonProperty("stt_text")
    private String sttText;

    /**
     * 에러 메시지 (실패 시)
     */
    @JsonProperty("error")
    private String error;
}