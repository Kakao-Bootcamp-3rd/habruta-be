package com.imyme.mine.domain.learning.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Solo STT 응답 DTO (AI → Main)
 * Schema: {request_id, attempt_id, user_id, status (SUCCESS/FAIL), stt_text, error}
 */
public record SoloSttResponseDto(
    @JsonProperty("request_id") String requestId,
    @JsonProperty("attempt_id") Long attemptId,
    @JsonProperty("user_id") Long userId,
    @JsonProperty("status") String status,
    @JsonProperty("stt_text") String sttText,
    @JsonProperty("error") String error
) {}