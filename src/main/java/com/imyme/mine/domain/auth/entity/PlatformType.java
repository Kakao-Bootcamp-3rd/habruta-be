package com.imyme.mine.domain.auth.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlatformType {
    MOBILE_WEB("Mobile Web"),
    DESKTOP_WEB("Desktop Web");

    private final String description;
}
