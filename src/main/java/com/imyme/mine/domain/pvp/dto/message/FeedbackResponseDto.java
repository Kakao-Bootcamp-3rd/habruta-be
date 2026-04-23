package com.imyme.mine.domain.pvp.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * PvP 피드백 생성 응답 DTO (RabbitMQ 메시지)
 * AI Server → Main Server
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponseDto implements Serializable {

    /**
     * 요청 고유 ID (pass-through)
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 방 ID
     */
    @JsonProperty("room_id")
    private Long roomId;

    /**
     * 상태 (SUCCESS, FAIL)
     */
    @JsonProperty("status")
    private String status;

    /**
     * 에러 메시지 (실패 시)
     */
    @JsonProperty("error")
    private String error;

    /**
     * 피드백 목록 (2명)
     */
    @JsonProperty("feedbacks")
    private List<UserFeedback> feedbacks;

    /**
     * 유저별 피드백
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserFeedback implements Serializable {

        /**
         * 유저 ID
         */
        @JsonProperty("user_id")
        private Long userId;

        /**
         * 점수 (0-100)
         */
        @JsonProperty("score")
        private Integer score;

        /**
         * 한 줄 요약평
         */
        @JsonProperty("summary")
        private String summary;

        /**
         * 키워드 분석 (포함/누락)
         */
        @JsonProperty("keywords")
        private List<String> keywords;

        /**
         * 사실 관계 확인 텍스트
         */
        @JsonProperty("facts")
        private String facts;

        /**
         * 이해 깊이 평가
         */
        @JsonProperty("understanding")
        private String understanding;

        /**
         * 상대방과 비교한 전략적 피드백
         */
        @JsonProperty("personalized_feedback")
        private String personalizedFeedback;
    }
}