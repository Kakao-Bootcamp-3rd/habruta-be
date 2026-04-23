package com.imyme.mine.domain.notification.entity;

/**
 * 알림 제공자 (푸시 알림 서비스)
 */
public enum NotificationProvider {
    FCM,    // Firebase Cloud Messaging (Android & Web)
    APNS,   // Apple Push Notification Service (iOS)
    WEB     // Web Push
}