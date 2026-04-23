package com.imyme.mine.domain.pvp.messaging;

/**
 * PvP 메시지 타입
 * - 클라이언트가 메시지를 구분하여 처리할 수 있도록 타입 정의
 */
public enum PvpMessageType {
    STATUS_CHANGE,
    GUEST_JOINED,
    GUEST_LEFT,
    HOST_LEFT,
    THINKING_STARTED,
    RECORDING_STARTED,
    READY,
    ANSWER_SUBMITTED,
    ANALYSIS_COMPLETED
}