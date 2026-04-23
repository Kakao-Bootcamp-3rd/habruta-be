package com.imyme.mine.domain.pvp.entity;

/**
 * PvP 제출 상태
 */
public enum PvpSubmissionStatus {
    PENDING,     // 업로드 대기 (URL 발급)
    UPLOADED,    // 분석 대기 (Queue 진입)
    PROCESSING,  // 분석 중 (STT 변환 중 또는 LLM 채점 중)
    COMPLETED,   // 완료 (STT와 AI 피드백 저장 완료)
    FAILED       // 실패 (STT 변환 실패, AI API 타임아웃 등)
}