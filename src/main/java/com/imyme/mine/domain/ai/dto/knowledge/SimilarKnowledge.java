package com.imyme.mine.domain.ai.dto.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Knowledge Evaluation API - 유사 지식
 * - 벡터 유사도 검색으로 찾은 기존 지식
 */
public record SimilarKnowledge(

    // 기존 지식 ID
    @NotBlank(message = "지식 ID는 필수입니다.")
    String id,

    // 기존 지식 텍스트
    @NotBlank(message = "지식 텍스트는 필수입니다.")
    String text,

    // 코사인 유사도 (0.0 ~ 1.0) : 1.0에 가까울수록 유사
    @NotNull(message = "유사도는 필수입니다.")
    @Min(value = 0, message = "유사도는 0 이상이어야 합니다.")
    @Max(value = 1, message = "유사도는 1 이하여야 합니다.")
    Double similarity
) {
}
