package com.imyme.mine.domain.ai.dto.solo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 서버 Solo API 공통 응답 래퍼
 * @param <T> data 필드의 타입
 */
@Getter
@Setter
@NoArgsConstructor
public class AiSoloResponse<T> {
    private Boolean success;
    private T data;
    private String error;
}