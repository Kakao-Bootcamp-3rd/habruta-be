-- E2E 테스트용 고정 사용자 추가
-- KAKAO provider + 고정 oauthId로 E2E 전용 계정 사용
-- (repeatable migration이므로 매번 실행되어도 안전)

INSERT INTO users (
    oauth_id,
    oauth_provider,
    email,
    nickname,
    profile_image_url,
    role,
    level,
    total_card_count,
    active_card_count,
    consecutive_days,
    win_count,
    last_login_at,
    created_at,
    updated_at
) VALUES (
    'e2e_test_user',
    'KAKAO',
    'e2e@test.com',
    '__e2e_test_user__',
    NULL,
    'USER',
    1,
    0,
    0,
    1,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (oauth_id, oauth_provider) DO NOTHING;

-- E2E 소켓 테스트용 HOST 유저
INSERT INTO users (
    oauth_id,
    oauth_provider,
    email,
    nickname,
    profile_image_url,
    role,
    level,
    total_card_count,
    active_card_count,
    consecutive_days,
    win_count,
    last_login_at,
    created_at,
    updated_at
) VALUES (
    'e2e_socket_host',
    'KAKAO',
    'e2e_host@test.com',
    '__e2e_socket_host__',
    NULL,
    'USER',
    1,
    0,
    0,
    1,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (oauth_id, oauth_provider) DO NOTHING;

-- E2E 소켓 테스트용 GUEST 유저
INSERT INTO users (
    oauth_id,
    oauth_provider,
    email,
    nickname,
    profile_image_url,
    role,
    level,
    total_card_count,
    active_card_count,
    consecutive_days,
    win_count,
    last_login_at,
    created_at,
    updated_at
) VALUES (
    'e2e_socket_guest',
    'KAKAO',
    'e2e_guest@test.com',
    '__e2e_socket_guest__',
    NULL,
    'USER',
    1,
    0,
    0,
    1,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (oauth_id, oauth_provider) DO NOTHING;