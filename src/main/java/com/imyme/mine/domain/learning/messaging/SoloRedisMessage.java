package com.imyme.mine.domain.learning.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Solo SSE 브로드캐스트용 Redis Pub/Sub 메시지 DTO
 * - 채널: solo:result:{attemptId}
 * - PUSH: 중간 이벤트 (연결 유지) — STT 완료 후 피드백 생성 단계 전환 알림
 * - EMIT: 최종 이벤트 (연결 종료) — 분석 완료/실패
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoloRedisMessage {

    public enum Type { PUSH, EMIT }

    /** 이벤트 종류 (PUSH: 연결 유지, EMIT: 연결 종료) */
    private Type type;

    /** 학습 시도 ID */
    private Long attemptId;

    /** 상태값 (PROCESSING, COMPLETED, FAILED) */
    private String status;

    /** 단계 (AUDIO_ANALYSIS, FEEDBACK_GENERATION — PUSH 타입일 때만 사용) */
    private String step;

    public static SoloRedisMessage push(Long attemptId, String status, String step) {
        return SoloRedisMessage.builder()
            .type(Type.PUSH)
            .attemptId(attemptId)
            .status(status)
            .step(step)
            .build();
    }

    public static SoloRedisMessage emit(Long attemptId, String status) {
        return SoloRedisMessage.builder()
            .type(Type.EMIT)
            .attemptId(attemptId)
            .status(status)
            .build();
    }
}