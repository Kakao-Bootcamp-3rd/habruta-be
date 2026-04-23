package com.imyme.mine.domain.challenge.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 서버 → Spring: 챌린지 토너먼트 최종 완료 MQ DTO
 *
 * <p>AI 서버가 {@code challenge.final.done} 큐로 발행하는 경량 메시지.
 * 실제 랭킹/피드백 데이터는 MQ가 아닌 Redis에서 직접 읽음:
 * <ul>
 *   <li>{@code challenge:{id}:final_ranking} — LRANGE로 순위별 attemptId 목록</li>
 *   <li>{@code challenge:{id}:feedbacks}     — HGETALL로 attemptId별 피드백 JSON</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
public class ChallengeRankingCompletedDto {

    /** "job:{challengeId}" 형식 */
    @JsonProperty("job_id")
    private String jobId;

    /** 완료 상태 (e.g. "RANKING_COMPLETED") */
    private String status;
}