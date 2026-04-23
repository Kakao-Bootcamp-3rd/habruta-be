package com.imyme.mine.domain.notification.entity;

/**
 * 알림 전송 상태
 */
public enum NotificationStatus {
    PENDING,    // 전송 대기
    SENT,       // 전송 완료
    DELIVERED,  // 수신 확인
    FAILED      // 전송 실패
}