package com.imyme.mine.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.notification.entity.NotificationPreference;

public record NotificationPreferencesResponse(
    String message,
    Preferences preferences
) {

    public record Preferences(
        @JsonProperty("allow_growth") boolean allowGrowth,
        @JsonProperty("allow_solo_result") boolean allowSoloResult,
        @JsonProperty("allow_pvp_result") boolean allowPvpResult,
        @JsonProperty("allow_challenge") boolean allowChallenge,
        @JsonProperty("allow_system") boolean allowSystem,
        @JsonProperty("allow_inactivity") boolean allowInactivity
    ) {}

    public static NotificationPreferencesResponse from(NotificationPreference pref) {
        return new NotificationPreferencesResponse(
            "알림 설정이 성공적으로 변경되었습니다.",
            new Preferences(
                Boolean.TRUE.equals(pref.getAllowGrowth()),
                Boolean.TRUE.equals(pref.getAllowSoloResult()),
                Boolean.TRUE.equals(pref.getAllowPvpResult()),
                Boolean.TRUE.equals(pref.getAllowChallenge()),
                Boolean.TRUE.equals(pref.getAllowSystem()),
                Boolean.TRUE.equals(pref.getAllowInactivity())
            )
        );
    }
}