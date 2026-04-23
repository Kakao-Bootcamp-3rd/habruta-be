package com.imyme.mine.domain.forbidden.entity;

public enum ForbiddenWordType {
    COMMON,     // 공통 (닉네임 + 방이름 등 전체 적용)
    NICKNAME,   // 닉네임에서만 금지
    ROOM_NAME   // 방제에서만 금지
}
