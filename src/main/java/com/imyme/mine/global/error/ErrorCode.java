package com.imyme.mine.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드 정의
 * - error: 대문자 스네이크 케이스
 * - message: 사용자에게 표시할 친화적 메시지
 * - status: HTTP 상태 코드
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ========== 인증/인가 (401, 403) ==========
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 토큰이 없거나 만료되었습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다. 다시 로그인해주세요."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "세션이 만료되었거나 로그아웃되었습니다. 다시 로그인해주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "방 참여자가 아닙니다."),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),

    // ========== OAuth (400, 500) ==========
    INVALID_OAUTH_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 OAuth 인증 코드입니다."),
    INVALID_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 OAuth 제공자입니다."),
    INVALID_ORIGIN(HttpStatus.BAD_REQUEST, "등록되지 않은 Origin입니다."),
    INVALID_ENVIRONMENT(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 환경입니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "OAuth 제공자 서버에 일시적인 문제가 발생했습니다."),
    NICKNAME_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "닉네임 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    SESSION_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "세션 생성에 실패했습니다. 다시 로그인해주세요."),

    // ========== 리소스 없음 (404) ==========
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "시도를 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    KEYWORD_NOT_FOUND(HttpStatus.NOT_FOUND, "키워드를 찾을 수 없습니다."),
    KNOWLEDGE_NOT_FOUND(HttpStatus.NOT_FOUND, "지식을 찾을 수 없습니다."),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "기기 정보를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "PvP 방을 찾을 수 없습니다."),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출 정보를 찾을 수 없습니다."),
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "챌린지를 찾을 수 없습니다."),
    PARTICIPATION_NOT_FOUND(HttpStatus.NOT_FOUND, "참여 기록을 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // ========== 잘못된 요청 (400) ==========
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "유효하지 않은 상태입니다."),
    INVALID_AUDIO_URL(HttpStatus.BAD_REQUEST, "유효하지 않은 오디오 URL입니다."),
    INVALID_CARD_ATTEMPT_MISMATCH(HttpStatus.BAD_REQUEST, "카드와 시도 ID가 일치하지 않습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서입니다."),
    INVALID_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 타입입니다."),
    INVALID_AGENT_TYPE(HttpStatus.BAD_REQUEST, "유효하지 않은 Agent Type입니다."),
    INVALID_PLATFORM_TYPE(HttpStatus.BAD_REQUEST, "유효하지 않은 Platform Type입니다."),
    INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    CHECKSUM_MISMATCH(HttpStatus.BAD_REQUEST, "파일 체크섬이 일치하지 않습니다."),
    INVALID_OBJECT_KEY(HttpStatus.BAD_REQUEST, "요청된 objectKey가 발급된 것과 일치하지 않습니다."),

    // ========== 충돌 (409) ==========
    NICKNAME_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    ALREADY_PARTICIPATED(HttpStatus.CONFLICT, "이미 참여한 챌린지입니다."),
    ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 리소스입니다."),
    CANNOT_DELETE_UPLOADED(HttpStatus.CONFLICT, "AI 분석 대기 중인 시도는 삭제할 수 없습니다."),
    ROOM_FULL(HttpStatus.CONFLICT, "방이 가득 찼습니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 방입니다."),
    DUPLICATE_ROOM(HttpStatus.CONFLICT, "이미 생성한 대기 중인 방이 있습니다."),
    ROOM_ALREADY_MATCHED(HttpStatus.CONFLICT, "이미 다른 사용자가 입장했습니다."),
    CANNOT_JOIN_OWN_ROOM(HttpStatus.BAD_REQUEST, "본인이 만든 방에는 입장할 수 없습니다."),
    ROOM_CANNOT_BE_DELETED(HttpStatus.FORBIDDEN, "게스트 입장 후에는 방을 삭제할 수 없습니다."),
    GAME_ALREADY_STARTED(HttpStatus.FORBIDDEN, "게임이 시작되어 나갈 수 없습니다."),
    MAX_ATTEMPTS_EXCEEDED(HttpStatus.CONFLICT, "최대 시도 횟수(5회)를 초과했습니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 녹음을 제출했습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 너무 큽니다. (최대 100MB)"),
    INVALID_ROOM_STATUS(HttpStatus.FORBIDDEN, "현재 방 상태에서는 이 작업을 수행할 수 없습니다."),

    // ========== 검증 실패 (400, 422) ==========
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "입력값 검증에 실패했습니다."),
    FORBIDDEN_WORD(HttpStatus.UNPROCESSABLE_ENTITY, "금지어가 포함되어 있습니다."),
    NICKNAME_LENGTH(HttpStatus.UNPROCESSABLE_ENTITY, "닉네임은 1~20자여야 합니다."),
    TITLE_LENGTH(HttpStatus.UNPROCESSABLE_ENTITY, "제목은 1~100자여야 합니다."),
    EMPTY_TITLE(HttpStatus.BAD_REQUEST, "제목이 비어있습니다."),
    INVALID_TITLE_LENGTH(HttpStatus.BAD_REQUEST, "제목은 1~20자여야 합니다."),

    // ========== 챌린지 상태 (400, 410) ==========
    CHALLENGE_NOT_STARTED(HttpStatus.BAD_REQUEST, "챌린지가 아직 시작되지 않았습니다."),
    CHALLENGE_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "결과가 아직 발표되지 않았습니다."),
    UPLOAD_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "S3 업로드가 완료되지 않았습니다."),

    // ========== 타임아웃/만료 (410) ==========
    UPLOAD_EXPIRED(HttpStatus.GONE, "업로드 제한 시간이 초과되었습니다."),
    ROOM_EXPIRED(HttpStatus.GONE, "방이 만료되었습니다."),
    CHALLENGE_ENDED(HttpStatus.GONE, "종료된 챌린지입니다."),
    CHALLENGE_NOT_OPEN(HttpStatus.FORBIDDEN, "아직 시작되지 않은 챌린지입니다."),

    // ========== Rate Limit (429) ==========
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도해주세요."),

    // ========== Knowledge API (400, 408, 500) ==========
    INVALID_FEEDBACK(HttpStatus.BAD_REQUEST, "피드백 형식이 올바르지 않거나 길이가 초과되었습니다."),
    TOO_MANY_ITEMS(HttpStatus.BAD_REQUEST, "배치 항목이 최대 개수(100개)를 초과했습니다."),
    INVALID_CANDIDATE(HttpStatus.BAD_REQUEST, "지식 후보 형식이 올바르지 않습니다."),
    TOO_MANY_SIMILARS(HttpStatus.BAD_REQUEST, "유사 지식 항목이 최대 개수(10개)를 초과했습니다."),
    LLM_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "LLM 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."),
    EMBEDDING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "임베딩 벡터 생성 중 오류가 발생했습니다."),
    LLM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "LLM 처리 중 오류가 발생했습니다."),

    // ========== 서버 오류 (500, 503) ==========
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스에 일시적인 문제가 발생했습니다."),
    AI_ANALYSIS_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AI 심층 분석에 실패했습니다."),
    AI_POLLING_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 분석 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."),
    FEEDBACK_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "피드백 저장에 실패했습니다."),
    CACHE_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "캐시 서버에 일시적인 문제가 발생했습니다."),
    S3_UPLOAD_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "파일 업로드 서비스에 일시적인 문제가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    // Enum name을 에러 코드로 사용 (대문자 스네이크 케이스) 예: UNAUTHORIZED, USER_NOT_FOUND
    public String getCode() {
        return this.name();
    }
}
