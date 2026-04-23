package com.imyme.mine.domain.ai.dto.knowledge;

import jakarta.validation.constraints.NotBlank;

/**
 * Knowledge Evaluation API - 지식 후보
 * - 새로운 지식 후보 정보
 */
public record EvaluationCandidate(

    // 정제된 지식 텍스트
    @NotBlank(message = "지식 텍스트는 필수입니다.")
    String text,

    // 원본 피드백 ID (추적용)
    @NotBlank(message = "원본 소스 ID는 필수입니다.")
    String sourceId
) {
}
