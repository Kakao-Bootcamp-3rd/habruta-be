package com.imyme.mine.domain.learning.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge 배치 실행 결과
 * - 배치 작업 통계 및 오류 정보
 */
public record KnowledgeBatchResult(
    LocalDateTime startTime,
    LocalDateTime endTime,
    int totalKeywords,
    int successKeywords,
    int failedKeywords,
    int totalFeedbacks,
    int createdKnowledge,
    int updatedKnowledge,
    int ignoredCandidates,
    List<String> errors
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int totalKeywords;
        private int successKeywords;
        private int failedKeywords;
        private int totalFeedbacks;
        private int createdKnowledge;
        private int updatedKnowledge;
        private int ignoredCandidates;
        private List<String> errors = new ArrayList<>();

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder totalKeywords(int totalKeywords) {
            this.totalKeywords = totalKeywords;
            return this;
        }

        public Builder successKeywords(int successKeywords) {
            this.successKeywords = successKeywords;
            return this;
        }

        public Builder failedKeywords(int failedKeywords) {
            this.failedKeywords = failedKeywords;
            return this;
        }

        public Builder totalFeedbacks(int totalFeedbacks) {
            this.totalFeedbacks = totalFeedbacks;
            return this;
        }

        public Builder createdKnowledge(int createdKnowledge) {
            this.createdKnowledge = createdKnowledge;
            return this;
        }

        public Builder updatedKnowledge(int updatedKnowledge) {
            this.updatedKnowledge = updatedKnowledge;
            return this;
        }

        public Builder ignoredCandidates(int ignoredCandidates) {
            this.ignoredCandidates = ignoredCandidates;
            return this;
        }

        public Builder addError(String error) {
            this.errors.add(error);
            return this;
        }

        public KnowledgeBatchResult build() {
            return new KnowledgeBatchResult(
                startTime,
                endTime,
                totalKeywords,
                successKeywords,
                failedKeywords,
                totalFeedbacks,
                createdKnowledge,
                updatedKnowledge,
                ignoredCandidates,
                errors
            );
        }
    }
}