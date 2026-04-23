package com.imyme.mine.domain.pvp.messaging;

/**
 * PvP Redis Pub/Sub 채널 정의
 * - 방 단위로 채널을 격리하여 불필요한 메시지 수신 방지
 */
public class PvpChannels {

    /**
     * 방별 채널 패턴: pvp:room:{roomId}
     *
     * @param roomId 방 ID
     * @return 채널명
     */
    public static String getRoomChannel(Long roomId) {
        return "pvp:room:" + roomId;
    }

    /**
     * 전체 PvP 채널 (전역 알림용, 선택적 사용)
     */
    public static final String GLOBAL_CHANNEL = "pvp:global";
}