package com.imyme.mine.domain.pvp.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * PvP 피드백 생성 요청 DTO (RabbitMQ 메시지)
 * Main Server → AI Server
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackRequestDto implements Serializable {

    /**
     * 요청 고유 ID (중복 처리 방지)
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 방 ID
     */
    @JsonProperty("room_id")
    private Long roomId;

    /**
     * 요청 시간 (Unix timestamp, 초 단위)
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    /**
     * 채점 기준표
     */
    @JsonProperty("criteria")
    private Criteria criteria;

    /**
     * 대결 참여 유저 데이터 (2명)
     */
    @JsonProperty("users")
    private List<UserAnswer> users;

    /**
     * 채점 기준표
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Criteria implements Serializable {

        /**
         * 필수로 포함해야 할 핵심 키워드
         */
        @JsonProperty("keyword")
        private String keyword;

        /**
         * 모범 답안 예시 텍스트
         */
        @JsonProperty("model_answer")
        private String modelAnswer;
    }

    /**
     * 유저 답변 정보
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserAnswer implements Serializable {

        /**
         * 유저 ID
         */
        @JsonProperty("user_id")
        private Long userId;

        /**
         * STT 변환된 텍스트
         */
        @JsonProperty("user_text")
        private String userText;
    }
}