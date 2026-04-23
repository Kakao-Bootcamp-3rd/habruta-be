package com.imyme.mine.domain.ai.dto.knowledge;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Knowledge Evaluation API 응답
 * - LLM의 판단 결과 (UPDATE 또는 IGNORE)
 */
public record KnowledgeEvaluationResponse(

    // 처리 성공 여부
    Boolean success,

    // 응답 데이터
    Data data,

    // 에러 정보 (실패 시)
    String error
) {

    // 응답 데이터 내부 구조
    public record Data(

        /**
         * 결정 타입
         * - "UPDATE": 기존 지식 개선
         * - "IGNORE": 중복이므로 무시
         */
        String decision,

        /**
         * 대상 지식 ID
         * - UPDATE 시: 업데이트할 기존 지식 ID
         * - IGNORE 시: null
         */
        @JsonProperty("targetId")
        String targetId,

        /**
         * 최종 지식 콘텐츠
         * - UPDATE 시: 개선된 지식 텍스트
         * - IGNORE 시: null
         */
        @JsonProperty("finalContent")
        String finalContent,

        /**
         * 최종 벡터 임베딩
         * - UPDATE 시: 새로운 임베딩 벡터 (1024차원)
         * - IGNORE 시: null
         */
        @JsonProperty("finalVector")
        List<Double> finalVector,

        /**
         * LLM의 판단 근거 (로깅용)
         */
        String reasoning
    ) {
    }
}
