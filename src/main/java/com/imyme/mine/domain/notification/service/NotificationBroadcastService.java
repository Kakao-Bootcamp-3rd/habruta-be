package com.imyme.mine.domain.notification.service;

import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 전체 사용자 알림 브로드캐스트 서비스
 *
 * <p>단순 루프 방식(N 트랜잭션 + FCM 발송 폭발)의 스케일 문제를 해결하기 위해
 * {@link #CHUNK_SIZE} 단위로 유저를 분할 조회하여 순차 처리합니다.
 * {@code @Async}로 실행되므로 스케줄러 스레드를 즉시 반환합니다.
 *
 * <pre>
 * [CHALLENGE_OPEN 브로드캐스트 흐름]
 * afterCommit()
 *   └─ broadcastToAllActive() → @Async (별도 스레드)
 *        └─ OFFSET 0, 500, 1000, ... 단위로 유저 조회
 *             └─ 각 유저별 NotificationCreatorService.create()
 *                  └─ DB 저장 + FCM 이벤트 발행 (개별 @Async)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationBroadcastService {

    private final UserRepository userRepository;
    private final NotificationCreatorService notificationCreatorService;

    private static final int CHUNK_SIZE = 500;

    /**
     * 전체 활성 유저에게 비동기 배치 알림 발송
     *
     * <p>호출 즉시 반환되며, 실제 처리는 별도 스레드에서 진행됩니다.
     * CHUNK_SIZE 단위로 분할 조회하여 메모리 사용량과 트랜잭션 크기를 제한합니다.
     */
    @Async
    public void broadcastToAllActive(NotificationType type, String title, String content,
                                     Long referenceId, String referenceType) {
        log.info("[NotificationBroadcast] 브로드캐스트 시작: type={}", type);

        int offset = 0;
        int total = 0;
        List<Long> chunk;

        do {
            chunk = userRepository.findAllActiveIdsPaged(offset, CHUNK_SIZE);
            for (Long userId : chunk) {
                try {
                    notificationCreatorService.create(userId, type, title, content,
                            referenceId, referenceType);
                } catch (Exception e) {
                    log.warn("[NotificationBroadcast] 개별 알림 실패 - userId={}, type={}", userId, type, e);
                }
            }
            offset += chunk.size();
            total += chunk.size();
        } while (chunk.size() == CHUNK_SIZE);

        log.info("[NotificationBroadcast] 브로드캐스트 완료: type={}, 총 발송={}", type, total);
    }
}