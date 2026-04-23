package com.imyme.mine.domain.ai.dto.solo;

/**
 * Solo 분석 요청 응답 데이터
 * POST /api/v1/solo/submissions 응답의 data 필드
 */
public record SoloSubmissionData(
    Long attemptId,
    String status  // "pending"
) {
}