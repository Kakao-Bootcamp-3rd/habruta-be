package com.imyme.mine.global.common.validation;

/**
 * Validation 그룹 정의
 * - 생성과 수정 시 다른 검증 규칙 적용
 */
public class ValidationGroups {

    // 생성 시 검증 그룹
    public interface Create {}

    // 수정 시 검증 그룹
    public interface Update {}
}
