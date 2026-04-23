/*
 * [V8] 운영 유지보수를 위한 DB 문서화 및 배치 정리 프로시저 추가
 * - 기존 테이블/컬럼에 Comment 추가 (DB 문서화)
 * - 데이터 정리를 위한 Stored Procedure 생성
 */

-- =========================================================
-- 1. 스키마 문서화 (테이블 및 컬럼 코멘트 추가)
-- =========================================================

-- 1. Users (회원)
COMMENT ON TABLE users IS '회원 정보 (소셜 로그인 기반)';
COMMENT ON COLUMN users.nickname IS '닉네임 (유니크, 20자 제한)';
COMMENT ON COLUMN users.level IS '사용자 레벨 (누적 카드 수에 따라 상승)';
COMMENT ON COLUMN users.last_login_at IS '마지막 접속 시간 (접속일 계산용)';
COMMENT ON COLUMN users.deleted_at IS '탈퇴 일시 (Soft Delete, 30일 후 영구 삭제)';

-- 2. Devices (기기)
COMMENT ON TABLE devices IS '사용자 기기 정보 (FCM 토큰 관리)';
COMMENT ON COLUMN devices.is_push_enabled IS '푸시 알림 수신 동의 여부';
COMMENT ON COLUMN devices.last_active_at IS '마지막 활동 시간 (6개월 미사용 시 비활성화 기준)';

-- 3. User Sessions (세션)
COMMENT ON TABLE user_sessions IS '인증 세션 (Refresh Token 관리)';
COMMENT ON COLUMN user_sessions.refresh_token IS '리프레시 토큰 (해시 저장 권장)';
COMMENT ON COLUMN user_sessions.expires_at IS '토큰 만료 일시 (만료 시 배치 삭제)';

-- 4. Knowledge Base (지식)
COMMENT ON TABLE knowledge_base IS 'RAG 시스템용 벡터 지식 베이스';
COMMENT ON COLUMN knowledge_base.embedding IS '1024차원 벡터 (qwen3-embedding-0.6b). 코사인 유사도 검색용.';
COMMENT ON COLUMN knowledge_base.search_vector IS '키워드 기반 검색을 위한 TSVECTOR (트리거로 자동 관리)';
COMMENT ON COLUMN knowledge_base.content_hash IS '중복 방지용 SHA-256 해시';

-- 5. Cards & Attempts (학습)
COMMENT ON TABLE cards IS '사용자별 학습 카드';
COMMENT ON COLUMN cards.attempt_count IS '카드 학습 시도 횟수';
COMMENT ON TABLE card_attempts IS '카드 학습 시도 이력 (녹음 데이터)';
COMMENT ON COLUMN card_attempts.audio_key IS 'S3 객체 키 (audio/{uuid}.m4a)';
COMMENT ON COLUMN card_attempts.status IS '학습 상태 (PENDING -> COMPLETED/FAILED)';


-- =========================================================
-- 2. 배치 정리용 프로시저 생성 (Batch Cleanup Procedures)
-- =========================================================

-- 2-1. 만료된 세션 정리 (Daily 03:00)
-- 설명: 만료일(expires_at)이 지난 세션을 물리적으로 삭제합니다.
CREATE OR REPLACE PROCEDURE batch_cleanup_expired_sessions(buffer_days INT DEFAULT 7)
LANGUAGE plpgsql
AS $$
BEGIN
    DELETE FROM user_sessions
    WHERE expires_at < NOW() - (buffer_days || ' days')::INTERVAL;

    RAISE NOTICE 'Expired sessions older than % days have been cleaned up.', buffer_days;
END;
$$;

-- 2-2. 장기 미사용 기기 비활성화 (Daily 03:00)
-- 설명: 마지막 활동(last_active_at)이 N개월 지난 기기를 Soft Delete 처리합니다.
CREATE OR REPLACE PROCEDURE batch_mark_inactive_devices_as_deleted(months INT DEFAULT 6)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE devices
    SET deleted_at = NOW()
    WHERE deleted_at IS NULL
      AND last_active_at < NOW() - (months || ' months')::INTERVAL;

    RAISE NOTICE 'Inactive devices older than % months have been marked as deleted.', months;
END;
$$;

-- 2-3. Soft Delete 데이터 영구 삭제 (Daily 03:00)
-- 설명: 탈퇴(deleted_at) 후 보존 기간(retention_days)이 지난 데이터를 영구 삭제합니다.
-- 주의: Cascade 설정에 의해 하위 데이터(세션, 카드 등)도 함께 삭제됩니다.
CREATE OR REPLACE PROCEDURE batch_purge_soft_deleted_data(retention_days INT DEFAULT 30)
LANGUAGE plpgsql
AS $$
BEGIN
    -- 1. 삭제된 기기 정보 영구 삭제
    DELETE FROM devices
    WHERE deleted_at IS NOT NULL
      AND deleted_at < NOW() - (retention_days || ' days')::INTERVAL;

    -- 2. 탈퇴한 회원 영구 삭제 (Cards, Sessions는 Cascade로 자동 삭제됨)
    DELETE FROM users
    WHERE deleted_at IS NOT NULL
      AND deleted_at < NOW() - (retention_days || ' days')::INTERVAL;

    RAISE NOTICE 'Soft deleted users/devices older than % days has been purged.', retention_days;
END;
$$;

-- 2-4. 중단된 학습 시도(Zombie Data) 정리 (Daily 03:00)
-- 설명: 'PENDING' 상태로 24시간이 지난 시도는 사용자가 이탈한 것으로 간주하고 영구 삭제합니다.
--      (시도 횟수 0인 상태로 남은 쓰레기 데이터 정리)
CREATE OR REPLACE PROCEDURE batch_cleanup_pending_attempts()
LANGUAGE plpgsql
AS $$
BEGIN
    -- [주의] 실제 운영 환경에서는 DELETE 전에 'audio_key'를 조회해서
    --       S3에서도 파일을 지우는 로직이 애플리케이션 레벨에 필요할 수 있습니다.
    --       (DB에서만 지우면 S3에 고아 파일이 남음)

    DELETE FROM card_attempts
    WHERE status = 'PENDING'
      AND created_at < NOW() - INTERVAL '24 hours';

    -- 결과 로깅
    RAISE NOTICE 'Abandoned pending attempts older than 24 hours have been cleaned up.';
END;
$$;
