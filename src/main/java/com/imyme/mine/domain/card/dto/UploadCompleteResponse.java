package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.CardAttempt;

public record UploadCompleteResponse(

    Long attemptId,
    String status,
    Integer durationSeconds,
    String message

) {

    public static UploadCompleteResponse from(CardAttempt attempt) {
        return new UploadCompleteResponse(
            attempt.getId(),
            attempt.getStatus().name(),
            attempt.getDurationSeconds(),
            "업로드가 완료되었습니다. AI 분석 대기 중입니다."
        );
    }
}