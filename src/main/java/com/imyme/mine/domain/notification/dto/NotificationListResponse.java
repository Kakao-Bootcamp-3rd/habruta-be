package com.imyme.mine.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.notification.entity.Notification;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public record NotificationListResponse(
    List<NotificationItem> notifications,
    Meta meta
) {

    public record Meta(
        int size,
        @JsonProperty("has_next") boolean hasNext,
        @JsonProperty("next_cursor") String nextCursor
    ) {}

    public static NotificationListResponse of(List<Notification> notifications, int size) {
        boolean hasNext = notifications.size() > size;
        List<Notification> result = hasNext ? notifications.subList(0, size) : notifications;

        String nextCursor = null;
        if (hasNext && !result.isEmpty()) {
            Notification last = result.get(result.size() - 1);
            nextCursor = encodeCursor(last);
        }

        return new NotificationListResponse(
            result.stream().map(NotificationItem::from).toList(),
            new Meta(size, hasNext, nextCursor)
        );
    }

    public static String encodeCursor(Notification n) {
        String raw = n.getCreatedAt().toString() + "_" + n.getId();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
