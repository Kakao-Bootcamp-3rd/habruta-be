package com.imyme.mine.domain.notification.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.notification.dto.MarkAllReadResponse;
import com.imyme.mine.domain.notification.dto.NotificationListResponse;
import com.imyme.mine.domain.notification.dto.NotificationPreferencesRequest;
import com.imyme.mine.domain.notification.dto.NotificationPreferencesResponse;
import com.imyme.mine.domain.notification.dto.UnreadCountResponse;
import com.imyme.mine.domain.notification.entity.Notification;
import com.imyme.mine.domain.notification.entity.NotificationPreference;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.repository.NotificationPreferenceRepository;
import com.imyme.mine.domain.notification.repository.NotificationRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public NotificationListResponse getNotifications(
        Long userId,
        Boolean isRead,
        String typeStr,
        String cursor,
        Integer sizeParam
    ) {
        int size = resolveSize(sizeParam);
        NotificationType type = resolveType(typeStr);

        // size + 1 개를 조회해서 hasNext 판단
        PageRequest pageable = PageRequest.of(0, size + 1);

        List<Notification> notifications;
        if (cursor == null || cursor.isBlank()) {
            notifications = notificationRepository.findNotificationsFirst(userId, isRead, type, pageable);
        } else {
            long[] decoded = decodeCursor(cursor);
            LocalDateTime cursorCreatedAt = LocalDateTime.ofEpochSecond(decoded[0], (int) decoded[1], java.time.ZoneOffset.UTC);
            long cursorId = decoded[2];
            notifications = notificationRepository.findNotificationsAfterCursor(userId, isRead, type, cursorCreatedAt, cursorId, pageable);
        }

        return NotificationListResponse.of(notifications, size);
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(Long userId, NotificationPreferencesRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        NotificationPreference pref = preferenceRepository.findByUserId(userId)
            .orElseGet(() -> NotificationPreference.defaultFor(user));

        pref.update(
            request.allowGrowth(),
            request.allowSoloResult(),
            request.allowPvpResult(),
            request.allowChallenge(),
            request.allowSystem(),
            request.allowInactivity()
        );
        return NotificationPreferencesResponse.from(preferenceRepository.save(pref));
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    @Transactional
    public MarkAllReadResponse markAllAsRead(Long userId) {
        int updatedCount = notificationRepository.markAllAsRead(userId);
        return MarkAllReadResponse.of(updatedCount);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 멱등성 보장: 이미 읽은 알림이어도 정상 처리
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.markAsRead();
        }
    }

    // ===== private helpers =====

    private int resolveSize(Integer sizeParam) {
        if (sizeParam == null) return DEFAULT_SIZE;
        return Math.min(sizeParam, MAX_SIZE);
    }

    private NotificationType resolveType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) return null;
        try {
            return NotificationType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
    }

    /**
     * 커서 디코딩: Base64(createdAt_id) → [epochSecond, nano, id]
     */
    private long[] decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            // 형식: "2026-01-10T15:42:00_2"
            int sep = raw.lastIndexOf('_');
            if (sep == -1) throw new BusinessException(ErrorCode.INVALID_CURSOR);

            LocalDateTime dt;
            try {
                dt = LocalDateTime.parse(raw.substring(0, sep));
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            long id = Long.parseLong(raw.substring(sep + 1));

            return new long[]{dt.toEpochSecond(java.time.ZoneOffset.UTC), dt.getNano(), id};
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}