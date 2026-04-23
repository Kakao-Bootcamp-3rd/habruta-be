package com.imyme.mine.domain.learning.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Solo Feedback 요청 DTO (Main → AI)
 * Schema: {request_id, attempt_id, user_id, stt_text, criteria: {keyword, model_answer}, history: [], timestamp}
 */
@Builder
public record SoloFeedbackRequestDto(
    @JsonProperty("request_id") String requestId,
    @JsonProperty("attempt_id") Long attemptId,
    @JsonProperty("user_id") Long userId,
    @JsonProperty("stt_text") String sttText,
    @JsonProperty("criteria") CriteriaDto criteria,
    @JsonProperty("history") List<Map<String, Object>> history,
    @JsonProperty("timestamp") Long timestamp
) {
    @Builder
    public record CriteriaDto(
        @JsonProperty("keyword") String keyword,
        @JsonProperty("model_answer") String modelAnswer
    ) {}
}