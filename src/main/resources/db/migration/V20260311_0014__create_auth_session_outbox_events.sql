CREATE TABLE auth_session_outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(30) NOT NULL,
    user_id BIGINT NOT NULL,
    device_uuid VARCHAR(36),
    expires_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_session_outbox_status_next_retry
    ON auth_session_outbox_events(status, next_retry_at);

CREATE INDEX idx_auth_session_outbox_created
    ON auth_session_outbox_events(created_at);
