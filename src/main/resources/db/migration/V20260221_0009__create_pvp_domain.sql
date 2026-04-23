-- =========================================================================
-- V2 Domain Schema (PvP & Notifications)
-- =========================================================================

-- -------------------------------------------------------------------------
-- 11. pvp_rooms (PvP 대결 방)
-- -------------------------------------------------------------------------
CREATE TABLE pvp_rooms (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    keyword_id BIGINT,
    room_name VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    host_user_id BIGINT,
    host_nickname VARCHAR(20) NOT NULL,
    guest_user_id BIGINT,
    guest_nickname VARCHAR(20),
    winner_user_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    matched_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT fk_rooms_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rooms_keyword FOREIGN KEY (keyword_id) REFERENCES keywords (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rooms_host FOREIGN KEY (host_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_rooms_guest FOREIGN KEY (guest_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_rooms_winner FOREIGN KEY (winner_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- 대기실 목록에서 '입장 가능한 방'만 빠르게 조회하기 위한 부분 인덱스
CREATE INDEX idx_rooms_open_category ON pvp_rooms (category_id, created_at DESC) WHERE status = 'OPEN';


-- -------------------------------------------------------------------------
-- 12. pvp_histories (PvP 대결 이력)
-- -------------------------------------------------------------------------
CREATE TABLE pvp_histories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    room_name VARCHAR(30) NOT NULL,
    role VARCHAR(10) NOT NULL,
    score INT NOT NULL,
    level INT NOT NULL,
    is_winner BOOLEAN NOT NULL,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    opponent_user_id BIGINT,
    opponent_nickname VARCHAR(20) NOT NULL,
    category_id BIGINT,
    category_name VARCHAR(20) NOT NULL,
    keyword_id BIGINT,
    keyword_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_histories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_histories_room FOREIGN KEY (room_id) REFERENCES pvp_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_histories_opponent FOREIGN KEY (opponent_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_histories_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT fk_histories_keyword FOREIGN KEY (keyword_id) REFERENCES keywords (id) ON DELETE SET NULL
);

CREATE INDEX idx_pvp_history_user ON pvp_histories (user_id, is_hidden, finished_at DESC);
CREATE INDEX idx_pvp_history_stats ON pvp_histories (user_id, is_winner, score);
CREATE INDEX idx_pvp_history_keyword ON pvp_histories (keyword_id, user_id, finished_at DESC);


-- -------------------------------------------------------------------------
-- 13. pvp_submissions (PvP 제출)
-- -------------------------------------------------------------------------
CREATE TABLE pvp_submissions (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    audio_url VARCHAR(500),
    duration_seconds INT,
    stt_text TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT uk_submissions_room_user UNIQUE (room_id, user_id),
    CONSTRAINT fk_submissions_room FOREIGN KEY (room_id) REFERENCES pvp_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_submissions_room ON pvp_submissions (room_id);
CREATE INDEX idx_submissions_status ON pvp_submissions (status, created_at);


-- -------------------------------------------------------------------------
-- 14. pvp_feedbacks (PvP 피드백)
-- -------------------------------------------------------------------------
CREATE TABLE pvp_feedbacks (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT,
    pvp_feedback_json JSONB NOT NULL,
    model_version VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_feedbacks_room_user UNIQUE (room_id, user_id),
    CONSTRAINT fk_feedbacks_room FOREIGN KEY (room_id) REFERENCES pvp_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedbacks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 방별 피드백 조회를 위한 부분 인덱스 (Soft delete 제외)
CREATE INDEX idx_pvp_feedbacks_room ON pvp_feedbacks (room_id) WHERE deleted_at IS NULL;


-- -------------------------------------------------------------------------
-- 19. notifications (알림)
-- -------------------------------------------------------------------------
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500),
    reference_id BIGINT,
    reference_type VARCHAR(20),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);


-- -------------------------------------------------------------------------
-- 20. notification_logs (알림 전송 이력)
-- -------------------------------------------------------------------------
CREATE TABLE notification_logs (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(10) NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT,
    retry_count SMALLINT NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_logs_notification FOREIGN KEY (notification_id) REFERENCES notifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_logs_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE,
    CONSTRAINT fk_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_logs_notification_status ON notification_logs (notification_id, status);
CREATE INDEX idx_logs_user_created ON notification_logs (user_id, created_at DESC);
CREATE INDEX idx_logs_status_retry ON notification_logs (status, retry_count, created_at);