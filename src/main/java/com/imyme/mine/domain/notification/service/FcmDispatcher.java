package com.imyme.mine.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.imyme.mine.domain.auth.entity.Device;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.DeviceRepository;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.notification.entity.Notification;
import com.imyme.mine.domain.notification.entity.NotificationLog;
import com.imyme.mine.domain.notification.entity.NotificationProvider;
import com.imyme.mine.domain.notification.event.NotificationCreatedEvent;
import com.imyme.mine.domain.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알림 FCM 발송 디스패처
 *
 * <p>원본 트랜잭션 커밋 후 비동기로 실행 → DB 롤백 시 FCM 미발송 보장
 * <ol>
 *   <li>사용자의 발송 가능한 기기 조회 (fcmToken 있음 + 푸시 수신 동의)</li>
 *   <li>기기별 NotificationLog PENDING 생성</li>
 *   <li>FCM data 메시지 발송</li>
 *   <li>결과에 따라 NotificationLog SENT / FAILED 업데이트</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmDispatcher {

    private final FcmSenderService fcmSenderService;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final NotificationLogRepository notificationLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(NotificationCreatedEvent event) {
        if (!fcmSenderService.isEnabled()) {
            log.debug("[FCM] Firebase 미초기화 — FCM 발송 건너뜀 (notificationId={})", event.notification().getId());
            return;
        }

        Notification notification = event.notification();
        Long userId = event.userId();

        List<Device> devices = deviceRepository.findPushableDevicesByUserId(userId);
        if (devices.isEmpty()) {
            log.debug("[FCM] 발송 대상 기기 없음 - userId={}, notificationId={}", userId, notification.getId());
            return;
        }

        User userRef = userRepository.getReferenceById(userId);
        Map<String, String> data = buildPayload(notification);

        for (Device device : devices) {
            NotificationLog notificationLog = NotificationLog.builder()
                    .notification(notification)
                    .device(device)
                    .user(userRef)
                    .provider(NotificationProvider.FCM)
                    .build();
            notificationLogRepository.save(notificationLog);

            try {
                fcmSenderService.send(device.getFcmToken(), data);
                notificationLog.markAsSent();
                log.info("[FCM] 전송 완료 - notificationId={}, deviceId={}", notification.getId(), device.getId());
            } catch (FirebaseMessagingException e) {
                String errorCode = e.getMessagingErrorCode() != null
                        ? e.getMessagingErrorCode().name() : "UNKNOWN";
                notificationLog.markAsFailed(errorCode, e.getMessage());
                log.warn("[FCM] 전송 실패 - notificationId={}, deviceId={}, errorCode={}",
                        notification.getId(), device.getId(), errorCode);
            } catch (Exception e) {
                notificationLog.markAsFailed("INTERNAL_ERROR", e.getMessage());
                log.error("[FCM] 예상치 못한 오류 - notificationId={}, deviceId={}",
                        notification.getId(), device.getId(), e);
            }
        }
    }

    private Map<String, String> buildPayload(Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("type", notification.getType().name());
        data.put("title", notification.getTitle());
        if (notification.getContent() != null) {
            data.put("content", notification.getContent());
        }
        if (notification.getReferenceId() != null) {
            data.put("referenceId", String.valueOf(notification.getReferenceId()));
        }
        if (notification.getReferenceType() != null) {
            data.put("referenceType", notification.getReferenceType());
        }
        data.put("path", resolvePath(notification));
        return data;
    }

    /**
     * 알림 타입별 프론트엔드 이동 경로
     *
     * <p>SOLO_RESULT: referenceId = cardId (호출 측에서 cardId로 전달할 것)
     * <p>PVP_RESULT:  referenceId = pvpRoomId
     * <p>CHALLENGE_*: referenceId = challengeId
     */
    private String resolvePath(Notification notification) {
        Long refId = notification.getReferenceId();
        return switch (notification.getType()) {
            case SOLO_RESULT -> "/mypage/cards/" + refId;
            case PVP_RESULT -> "/mypage/pvps/" + refId;
            case CHALLENGE_OPEN,
                 CHALLENGE_PERSONAL_RESULT,
                 CHALLENGE_OVERALL_RESULT -> "/challenge/ranking";
            case LEVEL_UP, SYSTEM -> "/main";
        };
    }
}