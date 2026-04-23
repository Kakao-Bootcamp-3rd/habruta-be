package com.imyme.mine.domain.pvp.messaging;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PvP Pub/Sub 메시지 DTO
 * - Redis를 통해 방 참여자들에게 실시간으로 전달되는 메시지
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PvpMessage {

    /**
     * 메시지 타입 (상태 변경, 입장 알림 등)
     */
    private PvpMessageType type;

    /**
     * 방 ID
     */
    private Long roomId;

    /**
     * 방 상태 (STATUS_CHANGE 타입일 때 사용)
     */
    private PvpRoomStatus status;

    /**
     * 메시지 내용 (선택적)
     */
    private String message;

    /**
     * 추가 데이터 (JSON 직렬화 가능한 객체)
     */
    private Object data;

    /**
     * 메시지 생성 시각
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 상태 변경 메시지 생성
     */
    public static PvpMessage statusChange(Long roomId, PvpRoomStatus status, String message) {
        return PvpMessage.builder()
                .type(PvpMessageType.STATUS_CHANGE)
                .roomId(roomId)
                .status(status)
                .message(message)
                .build();
    }

    /**
     * 게스트 입장 메시지 생성
     */
    public static PvpMessage guestJoined(Long roomId, Object guestInfo, String role) {
        return PvpMessage.builder()
                .type(PvpMessageType.GUEST_JOINED)
                .roomId(roomId)
                .message("게스트가 입장했습니다.")
                .data(guestInfo != null ? guestInfo : Map.of("role", role))
                .build();
    }

    /**
     * 게스트 나가기 메시지 생성
     */
    public static PvpMessage guestLeft(Long roomId, Long userId, String role) {
        return PvpMessage.builder()
                .type(PvpMessageType.GUEST_LEFT)
                .roomId(roomId)
                .message("게스트가 나갔습니다.")
                .data(Map.of("userId", userId, "role", role))
                .build();
    }

    public static PvpMessage hostLeft(Long roomId) {
        return PvpMessage.builder()
                .type(PvpMessageType.HOST_LEFT)
                .roomId(roomId)
                .status(PvpRoomStatus.CANCELED)
                .message("호스트가 퇴장하여 방이 취소되었습니다.")
                .build();
    }

    public static PvpMessage thinkingStarted(Long roomId, Long keywordId, String keywordName,
                                              LocalDateTime startedAt, LocalDateTime thinkingEndsAt) {
        Map<String, Object> data = Map.of(
                "keywordId", keywordId, "keywordName", keywordName,
                "startedAt", startedAt.toString(), "thinkingEndsAt", thinkingEndsAt.toString());
        return PvpMessage.builder()
                .type(PvpMessageType.THINKING_STARTED)
                .roomId(roomId)
                .status(PvpRoomStatus.THINKING)
                .message("키워드가 공개되었습니다! 생각 시간이 시작됩니다.")
                .data(data)
                .build();
    }

    public static PvpMessage recordingStarted(Long roomId) {
        return PvpMessage.builder()
                .type(PvpMessageType.RECORDING_STARTED)
                .roomId(roomId)
                .status(PvpRoomStatus.RECORDING)
                .message("생각 시간이 종료되었습니다! 녹음을 시작하세요.")
                .build();
    }

    public static PvpMessage ready(Long roomId, Long userId, String nickname, String role) {
        Map<String, Object> data = Map.of("userId", userId, "nickname", nickname, "role", role);
        return PvpMessage.builder()
                .type(PvpMessageType.READY)
                .roomId(roomId)
                .message("상대방이 준비되었습니다.")
                .data(data)
                .build();
    }

    public static PvpMessage answerSubmitted(Long roomId, Long userId, String nickname, String role) {
        Map<String, Object> data = Map.of("userId", userId, "nickname", nickname, "role", role);
        return PvpMessage.builder()
                .type(PvpMessageType.ANSWER_SUBMITTED)
                .roomId(roomId)
                .message("상대방이 답변을 제출했습니다.")
                .data(data)
                .build();
    }

    public static PvpMessage analysisCompleted(Long roomId) {
        return PvpMessage.builder()
                .type(PvpMessageType.ANALYSIS_COMPLETED)
                .roomId(roomId)
                .status(PvpRoomStatus.FINISHED)
                .message("AI 분석이 완료되었습니다.")
                .build();
    }
}