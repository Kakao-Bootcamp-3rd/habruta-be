package com.imyme.mine.domain.ai.dto.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/knowledge/evaluations
 *
 * AI 서버에 지식 후보와 유사 지식을 전송하여 업데이트 여부 결정 요청
 * - LLM이 중복성/보완성을 판단
 * - UPDATE: 기존 지식 개선 | IGNORE: 중복이므로 무시
 * - 동기 처리 (5~10초 내 응답)
 */
public record KnowledgeEvaluationRequest(

    // 새로운 지식 후보
    @NotNull(message = "지식 후보는 필수입니다.")
    @Valid
    EvaluationCandidate candidate,

    // 유사 지식 배열 (Top-k, 최대 10개) : 메인 서버가 벡터 검색으로 찾은 유사 지식 목록
    @NotNull(message = "유사 지식 목록은 필수입니다.")
    @Size(max = 10, message = "유사 지식은 최대 10개까지 가능합니다.")
    @Valid
    List<SimilarKnowledge> similars
) {
}
