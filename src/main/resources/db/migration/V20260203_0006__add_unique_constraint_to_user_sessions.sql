-- 유저 세션에서 유저와 기기 조합에 대한 고유 제약 조건 추가
ALTER TABLE user_sessions
ADD CONSTRAINT uk_sessions_user_device UNIQUE (user_id, device_id);
