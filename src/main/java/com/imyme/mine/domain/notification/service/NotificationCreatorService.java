package com.imyme.mine.domain.notification.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.notification.entity.Notification;
import com.imyme.mine.domain.notification.entity.NotificationPreference;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.event.NotificationCreatedEvent;
import com.imyme.mine.domain.notification.repository.NotificationPreferenceRepository;
import com.imyme.mine.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 생성 서비스
 *
 * <p>수신 설정 확인 → DB 저장 → FCM 발송 이벤트 발행.
 * 트랜잭션 커밋 후 {@link FcmDispatcher}가 비동기로 FCM 발송 처리.
 *
 * <h3>사용 예시</h3>
 * <pre>
 * notificationCreatorService.create(
 *     userId,
 *     NotificationType.CHALLENGE_PERSONAL_RESULT,
 *     "챌린지 결과가 나왔어요!",
 *     "오늘 챌린지 점수: 85점",
 *     challengeId,
 *     "CHALLENGE"
 * );
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCreatorService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 알림 생성 및 FCM 발송 트리거
     *
     * @param userId        수신 사용자 ID
     * @param type          알림 타입
     * @param title         알림 제목 (FCM payload title 포함)
     * @param content       알림 본문 (nullable)
     * @param referenceId   연관 엔티티 ID (nullable)
     * @param referenceType 연관 엔티티 타입 문자열 (nullable)
     */
    @Transactional
    public void create(Long userId, NotificationType type,
                       String title, String content,
                       Long referenceId, String referenceType) {

        // 수신 설정 확인 (미설정 시 기본값: 모든 알림 허용)
        NotificationPreference preference = notificationPreferenceRepository
                .findByUserId(userId)
                .orElse(null);

        if (preference != null && !isAllowed(preference, type)) {
            log.debug("[Notification] 수신 거부 설정 - userId={}, type={}", userId, type);
            return;
        }

        User userRef = userRepository.getReferenceById(userId);

        Notification notification = Notification.builder()
                .user(userRef)
                .type(type)
                .title(title)
                .content(content)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();

        notificationRepository.save(notification);
        log.info("[Notification] 알림 저장 - userId={}, type={}, notificationId={}",
                userId, type, notification.getId());

        eventPublisher.publishEvent(new NotificationCreatedEvent(notification, userId));
    }

    private boolean isAllowed(NotificationPreference preference, NotificationType type) {
        return switch (type) {
            case LEVEL_UP -> Boolean.TRUE.equals(preference.getAllowGrowth());
            case SOLO_RESULT -> Boolean.TRUE.equals(preference.getAllowSoloResult());
            case PVP_RESULT -> Boolean.TRUE.equals(preference.getAllowPvpResult());
            case CHALLENGE_OPEN,
                 CHALLENGE_PERSONAL_RESULT,
                 CHALLENGE_OVERALL_RESULT -> Boolean.TRUE.equals(preference.getAllowChallenge());
            case SYSTEM -> Boolean.TRUE.equals(preference.getAllowSystem());
        };
    }
}