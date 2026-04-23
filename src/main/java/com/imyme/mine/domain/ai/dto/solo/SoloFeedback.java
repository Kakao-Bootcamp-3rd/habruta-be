package com.imyme.mine.domain.ai.dto.solo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/**
 * Solo 분석 피드백 상세 내용
 */
public record SoloFeedback(
    String summarize,
    @JsonDeserialize(using = KeywordListDeserializer.class)
    List<String> keyword,
    String facts,
    String understanding,
    String personalized
) {
}