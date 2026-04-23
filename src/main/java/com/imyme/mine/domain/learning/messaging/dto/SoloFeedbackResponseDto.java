package com.imyme.mine.domain.learning.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.imyme.mine.domain.ai.dto.solo.KeywordListDeserializer;

import java.util.List;

/**
 * Solo Feedback 응답 DTO (AI → Main)
 * Schema: {request_id, attempt_id, user_id, status (SUCCESS/FAIL), feedback: {score, grade, summary, keywords, facts, understanding, personalized_feedback}, error}
 */
public record SoloFeedbackResponseDto(
    @JsonProperty("request_id") String requestId,
    @JsonProperty("attempt_id") Long attemptId,
    @JsonProperty("user_id") Long userId,
    @JsonProperty("status") String status,
    @JsonProperty("feedback") FeedbackDto feedback,
    @JsonProperty("error") String error
) {
    public record FeedbackDto(
        @JsonProperty("score") Integer score,
        @JsonProperty("grade") Integer grade,
        @JsonProperty("summary") String summary,
        @JsonProperty("keywords") @JsonDeserialize(using = KeywordListDeserializer.class) List<String> keywords,
        @JsonProperty("facts") String facts,
        @JsonProperty("understanding") String understanding,
        @JsonProperty("personalized_feedback") String personalizedFeedback
    ) {}
}