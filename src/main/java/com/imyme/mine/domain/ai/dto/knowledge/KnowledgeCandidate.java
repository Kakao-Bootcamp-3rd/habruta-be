package com.imyme.mine.domain.ai.dto.knowledge;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Knowledge Candidate - AI 서버가 생성한 지식 후보
 * - 정제된 텍스트 + 벡터 임베딩 (1024차원)
 */
public record KnowledgeCandidate(

    // 원본 피드백 ID
    String id,

    // 키워드명
    String keyword,

    // 정제된 지식 텍스트 : AI가 불필요한 내용을 제거하고 핵심만 추출
    @JsonProperty("refinedText")
    String refinedText,

    // 벡터 임베딩 (1024차원, float32) : OpenAI text-embedding-3-small 또는 유사 모델
    List<Double> embedding
) {
}
