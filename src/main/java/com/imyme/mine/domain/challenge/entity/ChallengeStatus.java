package com.imyme.mine.domain.challenge.entity;

public enum ChallengeStatus {

    /** 예정됨: 배치를 통해 생성되어 대기 중 */
    SCHEDULED,

    /** 진행 중: 22:00 ~ 22:09:59, 녹음 및 제출 가능 */
    OPEN,

    /** 마감됨: 22:10 이후, 신규 제출 차단 */
    CLOSED,

    /** 분석 중: AI 채점 워커 가동 중 */
    ANALYZING,

    /** 완료: AI 채점 및 랭킹 산출 완료, 결과 공개 */
    COMPLETED
}