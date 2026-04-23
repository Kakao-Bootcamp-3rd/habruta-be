package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;

/**
 * 검증된 학습 시도 DTO
 * - Card와 CardAttempt의 소유권 및 관계 검증 후 반환되는 DTO
 * - 중복 검증 로직 제거를 위한 공통 반환 타입
 */
public record ValidatedAttempt(
    Card card,
    CardAttempt attempt
) {
}