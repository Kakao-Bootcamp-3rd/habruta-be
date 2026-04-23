package com.imyme.mine.domain.ai.dto.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/knowledge/candidates/batch
 *
 * AI 서버에 피드백 배치를 전송하여 지식 후보 생성 요청
 * - 여러 피드백을 한 번에 처리하여 효율성 증대
 * - 동기 처리 (1~3초 내 응답)
 */
public record KnowledgeCandidateBatchRequest(

    // 피드백 항목 배열 (최대 100개)
    @NotNull(message = "피드백 항목은 필수입니다.")
    @Size(min = 1, max = 100, message = "피드백 항목은 1~100개여야 합니다.")
    @Valid
    List<FeedbackItem> items
) {
}
