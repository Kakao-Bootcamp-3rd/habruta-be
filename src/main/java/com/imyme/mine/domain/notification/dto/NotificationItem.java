package com.imyme.mine.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.notification.entity.Notification;

import java.time.Instant;
import java.time.ZoneOffset;

public record NotificationItem(
    Long id,
    String type,
    String title,
    String content,
    @JsonProperty("reference_id") Long referenceId,
    @JsonProperty("reference_type") String referenceType,
    @JsonProperty("is_read") boolean isRead,
    @JsonProperty("created_at") Instant createdAt
) {

    public static NotificationItem from(Notification n) {
        return new NotificationItem(
            n.getId(),
            n.getType().name(),
            n.getTitle(),
            n.getContent(),
            n.getReferenceId(),
            n.getReferenceType(),
            Boolean.TRUE.equals(n.getIsRead()),
            n.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
