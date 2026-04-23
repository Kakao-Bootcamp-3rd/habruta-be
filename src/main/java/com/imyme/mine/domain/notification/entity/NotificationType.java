package com.imyme.mine.domain.notification.entity;

/**
 * 알림 타입
 */
public enum NotificationType {
    // 1. 성장 & 학습 (Growth)
    LEVEL_UP,             // 레벨업

    // 2. 학습 & 대결 결과 (Results)
    SOLO_RESULT,                // 솔로 모드 채점 완료
    PVP_RESULT,                 // PvP 대결 결과

    // 3. 챌린지 (Challenge)
    CHALLENGE_OPEN,       // 챌린지 오픈
    CHALLENGE_PERSONAL_RESULT,  // 챌린지 내(개인) 채점 완료
    CHALLENGE_OVERALL_RESULT,   // 챌린지 최종 랭킹/전체 결과 발표

    // 4. 시스템 (System)
    SYSTEM                // 시스템 공지
}
