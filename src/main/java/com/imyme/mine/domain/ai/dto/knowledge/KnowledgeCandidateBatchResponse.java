package com.imyme.mine.domain.ai.dto.knowledge;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Knowledge Candidate Batch API 응답
 * - AI 서버가 생성한 지식 후보 목록
 */
public record KnowledgeCandidateBatchResponse(

    // 처리 성공 여부
    Boolean success,

    // 응답 데이터
    Data data,

    //에러 정보 (실패 시)
    String error
) {
    public record Data(
        // 처리된 항목 수
        @JsonProperty("processedCount")
        Integer processedCount,
        // 생성된 지식 후보 배열
        List<KnowledgeCandidate> candidates
    ) {
    }
}
