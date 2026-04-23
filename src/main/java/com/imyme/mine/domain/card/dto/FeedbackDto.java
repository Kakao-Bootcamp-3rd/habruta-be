package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.card.entity.CardFeedback;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 피드백 DTO
 * - COMPLETED 상태의 attempt에 포함되는 feedback 객체
 * - camelCase 필드명 사용
 */
@Slf4j
public record FeedbackDto(
    Integer overallScore,
    Short level,
    String summary,
    String keywords,
    String facts,
    String understanding,
    String socraticFeedback
) {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static FeedbackDto from(CardFeedback feedback) {
        if (feedback == null) {
            return null;
        }

        // feedbackJson JSONB 파싱
        String summary = null;
        String keywords = null;
        String facts = null;
        String understanding = null;
        String socraticFeedback = null;

        if (feedback.getFeedbackJson() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(feedback.getFeedbackJson());
                summary = getTextValue(jsonNode, "summary");
                keywords = getTextValue(jsonNode, "keywords");
                facts = getTextValue(jsonNode, "facts");
                understanding = getTextValue(jsonNode, "understanding");
                socraticFeedback = getTextValue(jsonNode, "socratic_feedback");
            } catch (JsonProcessingException e) {
                log.error("Failed to parse feedbackJson: {}", feedback.getFeedbackJson(), e);
            }
        }

        return new FeedbackDto(
            feedback.getOverallScore(),
            feedback.getLevel(),
            summary,
            keywords,
            facts,
            understanding,
            socraticFeedback
        );
    }

    private static String getTextValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
    }
}
