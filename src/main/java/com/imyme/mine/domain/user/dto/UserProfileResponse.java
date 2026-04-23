package com.imyme.mine.domain.user.dto;

import com.imyme.mine.domain.auth.entity.User;

/**
 * 사용자 프로필 응답 DTO
 * - 기본 정보 + 학습 통계 + 게이미피케이션 데이터
 */
public record UserProfileResponse(
    Long id,
    String email,
    String oauthProvider,
    String nickname,
    String profileImageUrl,

    // --- 게이미피케이션 ---
    Integer level,
    Integer nextLevelRemainingCards, // 다음 레벨까지 남은 카드 수 (계산됨) //TODO : 프론트 필요 여부 확인
    Integer levelProgressPercent,    // 현재 레벨 달성도 % (UI 바 표시용) //TODO : 프론트 필요 여부 확인

    // --- 통계 ---
    Integer totalCardCount,          // 누적 카드 수 (전체 학습량) //TODO : 프론트 필요 여부 확인
    Integer activeCardCount,
    Integer consecutiveDays,
    Integer winCount
) {
    public static UserProfileResponse from(User user, String profileImageUrl) {
        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getOauthProvider() != null ? user.getOauthProvider().name() : null,
            user.getNickname(),
            profileImageUrl,

            user.getLevel(),
            user.getRemainingCardsForNextLevel(),
            user.getLevelProgressPercent(),

            user.getTotalCardCount(),
            user.getActiveCardCount(),
            user.getConsecutiveDays(),
            user.getWinCount()
        );
    }
}
