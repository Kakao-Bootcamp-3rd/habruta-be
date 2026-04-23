package com.imyme.mine.domain.pvp.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * STT 변환 요청 DTO (RabbitMQ 메시지)
 * Main Server → AI Server
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SttRequestDto implements Serializable {

    /**
     * 요청 고유 ID (중복 처리 방지)
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
     * 변환할 S3 오디오 파일 URL
     */
    @JsonProperty("audio_url")
    private String audioUrl;

    /**
     * 요청 시간 (Unix timestamp, 초 단위)
     */
    @JsonProperty("timestamp")
    private Long timestamp;
}