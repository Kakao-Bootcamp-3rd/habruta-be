package com.imyme.mine.domain.ai.dto.solo;

/**
 * Solo 분석 결과 (점수 + 피드백)
 */
public record SoloResult(
    Integer overallScore,
    Integer level,
    SoloFeedback feedback
) {
}