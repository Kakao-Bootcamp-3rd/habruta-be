package com.imyme.mine.domain.pvp.messaging;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.global.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PvP 서비스에서 Redis Pub/Sub 사용 예시
 * - 실제 PvpRoomService에 통합하기 전 테스트용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PvpMessageUsageExample {

    private final MessagePublisher messagePublisher;

    /**
     * 예시 1: 게스트 입장 시 호스트에게 알림
     */
    public void notifyGuestJoined(Long roomId, Long guestId, String guestNickname) {
        // 게스트 정보 DTO 생성
        var guestInfo = new GuestInfo(guestId, guestNickname);

        // 메시지 발행
        PvpMessage message = PvpMessage.guestJoined(roomId, guestInfo, "GUEST");
        String channel = PvpChannels.getRoomChannel(roomId);

        messagePublisher.publish(channel, message);
        log.info("게스트 입장 알림 발행: roomId={}, guestId={}", roomId, guestId);
    }

    /**
     * 예시 2: 방 상태 변경 시 양쪽에게 알림
     */
    public void notifyStatusChange(Long roomId, PvpRoomStatus newStatus) {
        String statusMessage = switch (newStatus) {
            case MATCHED -> "매칭 완료! 잠시 후 키워드가 공개됩니다.";
            case THINKING -> "생각 시간이 시작되었습니다. (30초)";
            case RECORDING -> "녹음이 시작되었습니다.";
            case PROCESSING -> "AI 분석 중입니다.";
            case FINISHED -> "결과가 나왔습니다!";
            default -> "상태가 변경되었습니다.";
        };

        PvpMessage message = PvpMessage.statusChange(roomId, newStatus, statusMessage);
        String channel = PvpChannels.getRoomChannel(roomId);

        messagePublisher.publish(channel, message);
        log.info("상태 변경 알림 발행: roomId={}, status={}", roomId, newStatus);
    }

    /**
     * 예시 3: 답변 제출 시 상대방에게 알림
     */
    public void notifyAnswerSubmitted(Long roomId, Long userId, String nickname) {
        PvpMessage message = PvpMessage.answerSubmitted(roomId, userId, nickname, "HOST");
        String channel = PvpChannels.getRoomChannel(roomId);

        messagePublisher.publish(channel, message);
        log.info("답변 제출 알림 발행: roomId={}, userId={}", roomId, userId);
    }

    /**
     * 예시 4: AI 분석 완료 시 양쪽에게 알림
     */
    public void notifyAnalysisCompleted(Long roomId) {
        PvpMessage message = PvpMessage.analysisCompleted(roomId);
        String channel = PvpChannels.getRoomChannel(roomId);

        messagePublisher.publish(channel, message);
        log.info("AI 분석 완료 알림 발행: roomId={}", roomId);
    }

    // 내부 DTO
    private record GuestInfo(Long id, String nickname) {}
}