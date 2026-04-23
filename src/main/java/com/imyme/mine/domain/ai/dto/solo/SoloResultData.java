package com.imyme.mine.domain.ai.dto.solo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Solo 분석 결과 조회 응답 데이터
 * GET /api/v1/solo/submissions/{attemptId} 응답의 data 필드
 */
public record SoloResultData(
    Long attemptId,
    String status,  // "pending", "completed", "failed"

    @JsonProperty("result")
    SoloResult result  // status가 "completed"일 때만 존재
) {
}