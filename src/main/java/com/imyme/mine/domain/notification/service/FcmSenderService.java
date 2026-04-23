package com.imyme.mine.domain.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Firebase Cloud Messaging 발송 서비스
 * - data 전용 payload 사용 (앱에서 알림 표시 처리)
 * - FIREBASE_SERVICE_ACCOUNT_KEY 미설정 시 IllegalStateException 발생
 */
@Slf4j
@Service
public class FcmSenderService {

    /**
     * FCM data 메시지 발송
     *
     * @param fcmToken 수신 기기 FCM 토큰
     * @param data     전송할 key-value 데이터
     * @return FCM 메시지 ID
     * @throws FirebaseMessagingException FCM 발송 실패 시
     */
    public String send(String fcmToken, Map<String, String> data) throws FirebaseMessagingException {
        if (FirebaseApp.getApps().isEmpty()) {
            throw new IllegalStateException("Firebase가 초기화되지 않았습니다. FIREBASE_SERVICE_ACCOUNT_KEY를 확인하세요.");
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .putAllData(data)
                .build();

        String messageId = FirebaseMessaging.getInstance().send(message);
        log.debug("[FCM] 전송 성공 - token={}...{}, messageId={}",
                fcmToken.substring(0, Math.min(8, fcmToken.length())),
                fcmToken.substring(Math.max(0, fcmToken.length() - 4)),
                messageId);
        return messageId;
    }

    /**
     * Firebase 초기화 여부 확인 (로컬 개발 환경 스킵용)
     */
    public boolean isEnabled() {
        return !FirebaseApp.getApps().isEmpty();
    }
}