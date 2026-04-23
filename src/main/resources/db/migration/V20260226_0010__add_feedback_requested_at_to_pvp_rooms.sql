ALTER TABLE pvp_rooms
    ADD COLUMN feedback_requested_at TIMESTAMP NULL;

COMMENT ON COLUMN pvp_rooms.feedback_requested_at IS 'Feedback Request 최초 발행 시각 (중복 발행 방지용)';