package com.imyme.mine.domain.card.dto;

/**
 * 학습 시도 진행 단계 (PROCESSING 상태 세분화)
 */
public enum AttemptProcessingStep {
    AUDIO_ANALYSIS,       // STT 변환 중
    FEEDBACK_GENERATION   // AI 피드백 생성 중
}
