package com.imyme.mine.domain.auth.dto;

import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.entity.User;
import lombok.Builder;

@Builder
public record OAuthLoginResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn,
    UserInfo user
) {
    @Builder
    public record UserInfo(
        Long id,
        String oauthId,
        OAuthProviderType oauthProvider,
        String nickname,
        String profileImageUrl,
        Integer level,
        Integer totalCardCount,
        Integer activeCardCount,
        Integer consecutiveDays,
        Integer winCount,
        Boolean isNewUser
    ) {
        public static UserInfo from(User user, boolean isNewUser) {
            return UserInfo.builder()
                .id(user.getId())
                .oauthId(user.getOauthId())
                .oauthProvider(user.getOauthProvider())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .level(user.getLevel())
                .totalCardCount(user.getTotalCardCount())
                .activeCardCount(user.getActiveCardCount())
                .consecutiveDays(user.getConsecutiveDays())
                .winCount(user.getWinCount())
                .isNewUser(isNewUser)
                .build();
        }
    }
}
