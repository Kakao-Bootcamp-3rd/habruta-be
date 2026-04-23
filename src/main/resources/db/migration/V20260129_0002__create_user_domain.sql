-- 1. users 테이블
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    oauth_id VARCHAR(100) NOT NULL,
    oauth_provider VARCHAR(20) NOT NULL CHECK (oauth_provider IN ('KAKAO', 'GOOGLE', 'APPLE')),
    email VARCHAR(255),
    nickname VARCHAR(20) NOT NULL,
    profile_image_url VARCHAR(500),
    profile_image_key VARCHAR(200),
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    level INT NOT NULL DEFAULT 1 CHECK (level > 0),
    total_card_count INT NOT NULL DEFAULT 0,
    active_card_count INT NOT NULL DEFAULT 0,
    consecutive_days INT NOT NULL DEFAULT 1,
    win_count INT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_users_oauth_id_provider ON users(oauth_id, oauth_provider);
-- Partial Index: 탈퇴한 닉네임은 중복 허용
CREATE UNIQUE INDEX uk_users_nickname ON users(nickname) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL AND email IS NOT NULL;


-- 2. devices 테이블
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    device_uuid VARCHAR(36) NOT NULL,
    fcm_token TEXT,
    agent_type VARCHAR(20) NOT NULL,
    platform_type VARCHAR(20) NOT NULL,
    is_standalone BOOLEAN NOT NULL DEFAULT FALSE,
    last_user_id BIGINT,
    is_push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_devices_user FOREIGN KEY (last_user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Partial Index: 기기 재설치 시 UUID 중복 문제 해결
CREATE UNIQUE INDEX uk_devices_uuid ON devices(device_uuid) WHERE deleted_at IS NULL;
CREATE INDEX idx_devices_fcm_token ON devices(fcm_token) WHERE deleted_at IS NULL AND fcm_token IS NOT NULL;
CREATE INDEX idx_devices_last_user ON devices(last_user_id, last_active_at DESC) WHERE deleted_at IS NULL;


-- 3. user_sessions 테이블
CREATE TABLE user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    refresh_token VARCHAR(500) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sessions_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_sessions_refresh_token ON user_sessions(refresh_token);
CREATE INDEX idx_sessions_user ON user_sessions(user_id, last_used_at DESC);
CREATE INDEX idx_sessions_device ON user_sessions(device_id);
CREATE INDEX idx_sessions_expires ON user_sessions(expires_at);
