package com.imyme.mine.domain.challenge.dto.message;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 서버 → Spring: 챌린지 STT 응답 MQ DTO
 *
 * <p>AI 서버가 {@code challenge.feedback.response} 큐로 발행하는 메시지.
 * 채점/피드백은 토너먼트 완료 후 {@code challenge.ranking.completed}에서 수신.
 * status = "FAIL"인 경우 sttText는 null일 수 있음.
 */
@Getter
@NoArgsConstructor
public class ChallengeFeedbackResponseDto {

    private Long attemptId;
    private Long challengeId;

    /** "SUCCESS" 또는 "FAIL" */
    private String status;

    /** STT 변환 텍스트, FAIL 시 null */
    private String sttText;
}