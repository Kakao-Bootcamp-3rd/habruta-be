package com.imyme.mine.domain.notification.event;

import com.imyme.mine.domain.notification.entity.Notification;

/**
 * 알림 저장 완료 이벤트
 * - @TransactionalEventListener(AFTER_COMMIT)으로 FCM 발송 트리거
 * - userId는 detached proxy 없이 안전하게 접근하기 위해 별도 포함
 */
public record NotificationCreatedEvent(Notification notification, Long userId) {
}