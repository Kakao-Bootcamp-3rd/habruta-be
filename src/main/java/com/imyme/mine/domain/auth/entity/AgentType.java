package com.imyme.mine.domain.auth.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AgentType {
    CHROME("Chrome"),
    SAFARI("Safari"),
    SAMSUNG("Samsung Internet"),
    OTHER("Other");

    private final String description;
}
