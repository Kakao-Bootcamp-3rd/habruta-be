package com.imyme.mine.domain.pvp.dto;

/**
 * PvP WebSocket 메시지 타입
 */
public enum MessageType {
    // 방 관련
    ROOM_CREATE,        // 방 생성
    ROOM_JOIN,          // 방 참가
    ROOM_LEAVE,         // 방 나가기
    ROOM_CREATED,       // 방 생성됨 (서버 → 클라이언트)
    ROOM_JOINED,        // 유저 참가됨 (브로드캐스트)
    ROOM_LEFT,          // 유저 나감 (브로드캐스트)

    // 게임 상태
    PLAYER_READY,       // 플레이어 준비 완료 (브로드캐스트)
    GAME_START,         // 게임 시작 (서버 → 클라이언트)
    GAME_COUNTDOWN,     // 카운트다운
    STATUS_CHANGE,      // 방 상태 변경

    // 게임 진행
    QUESTION_PRESENTED, // 문제 제시
    ANSWER_SUBMIT,      // 답변 제출 (클라이언트 → 서버)
    ANSWER_SUBMITTED,   // 답변 제출됨 (서버 → 상대방)

    // 게임 종료
    ROUND_END,          // 라운드 종료
    GAME_END,           // 게임 종료

    // 에러
    ERROR               // 에러 메시지
}
