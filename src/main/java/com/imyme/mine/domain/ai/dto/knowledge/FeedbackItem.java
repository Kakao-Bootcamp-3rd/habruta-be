package com.imyme.mine.domain.ai.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Knowledge Candidate Batch API - 피드백 항목
 * - 원본 피드백 데이터를 AI 서버로 전송하기 위한 DTO
 */
public record FeedbackItem(

    // 피드백 고유 ID (원본 피드백 추적용)
    @NotBlank(message = "피드백 ID는 필수입니다.")
    String id,

    // 키워드명 (예: "프로세스", "스레드")
    @NotBlank(message = "키워드는 필수입니다.")
    String keyword,

    // 원본 피드백 텍스트  - feedback.personalized 내용
    @NotBlank(message = "피드백 텍스트는 필수입니다.")
    String rawFeedback
) {
}
