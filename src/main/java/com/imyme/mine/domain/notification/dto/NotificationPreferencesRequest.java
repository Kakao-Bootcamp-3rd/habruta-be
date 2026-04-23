package com.imyme.mine.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferencesRequest(
    @NotNull @JsonProperty("allow_growth") Boolean allowGrowth,
    @NotNull @JsonProperty("allow_solo_result") Boolean allowSoloResult,
    @NotNull @JsonProperty("allow_pvp_result") Boolean allowPvpResult,
    @NotNull @JsonProperty("allow_challenge") Boolean allowChallenge,
    @NotNull @JsonProperty("allow_system") Boolean allowSystem,
    @NotNull @JsonProperty("allow_inactivity") Boolean allowInactivity
) {}
