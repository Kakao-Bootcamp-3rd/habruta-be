package com.imyme.mine.domain.pvp.entity;

/**
 * PvP 방 상태
 */
public enum PvpRoomStatus {
    OPEN,        // 방 생성 (호스트 대기)
    MATCHED,     // 게스트 입장 (키워드 선정 중)
    THINKING,    // 생각 시간 카운트다운 (30초)
    RECORDING,   // 동시 녹음 중
    PROCESSING,  // AI 분석 중
    FINISHED,    // 결과 발표 완료
    CANCELED,    // 호스트 퇴장으로 인한 방 폭파
    EXPIRED      // 유령 방 정리 (배치 처리)
}