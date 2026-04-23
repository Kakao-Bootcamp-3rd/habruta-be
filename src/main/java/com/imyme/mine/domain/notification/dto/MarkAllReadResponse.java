package com.imyme.mine.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarkAllReadResponse(
    @JsonProperty("updated_count") int updatedCount,
    String message
) {

    public static MarkAllReadResponse of(int updatedCount) {
        String message = updatedCount == 0
            ? "읽지 않은 알림이 없습니다."
            : updatedCount + "개의 알림을 읽음 처리했습니다.";
        return new MarkAllReadResponse(updatedCount, message);
    }
}
