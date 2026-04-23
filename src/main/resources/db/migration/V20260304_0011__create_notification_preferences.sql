-- ============================================================
-- notification_preferences (알림 수신 설정, 유저당 1행)
-- ============================================================
CREATE TABLE notification_preferences
(
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    allow_growth      BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_solo_result BOOLEAN      NOT NULL DEFAULT TRUE, -- [추가] 솔로 모드 결과
    allow_pvp_result  BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_challenge   BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_system      BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_inactivity  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE notification_preferences IS '유저별 알림 수신 설정 (1유저 1행)';
COMMENT ON COLUMN notification_preferences.allow_growth IS 'LEVEL_UP 알림 수신 여부';
COMMENT ON COLUMN notification_preferences.allow_solo_result IS 'SOLO_RESULT 알림 수신 여부';
COMMENT ON COLUMN notification_preferences.allow_pvp_result IS 'PVP_RESULT 알림 수신 여부';
COMMENT ON COLUMN notification_preferences.allow_challenge IS 'CHALLENGE_OPEN, PERSONAL_RESULT, OVERALL_RESULT 수신 여부';
COMMENT ON COLUMN notification_preferences.allow_system IS 'SYSTEM 알림 수신 여부';
COMMENT ON COLUMN notification_preferences.allow_inactivity IS '미접속 리마인드 푸시 수신 여부 (DB 저장 안 됨)';
