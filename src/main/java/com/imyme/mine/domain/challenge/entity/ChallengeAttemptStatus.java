package com.imyme.mine.domain.challenge.entity;

public enum ChallengeAttemptStatus {

    /** 업로드 대기: URL만 발급받고 아직 S3 업로드 미완료 (v1 미사용) */
    PENDING,

    /** 분석 대기: 파일 업로드 완료, RabbitMQ 분석 이벤트 발행 직후 */
    UPLOADED,

    /** 분석 중: AI 워커가 STT 변환 및 LLM 채점 진행 중 (중복 처리 방지용 락) */
    PROCESSING,

    /** 완료: 분석 및 피드백 저장 정상 완료 */
    COMPLETED,

    /** 실패: STT 변환 실패, 외부 API 타임아웃 등 (배치 재시도 대상) */
    FAILED
}