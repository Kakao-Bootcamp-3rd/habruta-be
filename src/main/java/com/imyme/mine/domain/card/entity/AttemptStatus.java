package com.imyme.mine.domain.card.entity;

public enum AttemptStatus {
    PENDING,     // S3 업로드 대기 (10분 타임아웃)
    UPLOADED,    // AI 분석 대기
    PROCESSING,  // STT + AI 채점 중
    COMPLETED,   // 완료
    FAILED,      // 실패
    EXPIRED      // 업로드 제한 시간 초과 (10분)
}