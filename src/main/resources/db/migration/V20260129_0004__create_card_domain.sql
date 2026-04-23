-- 1. cards 테이블
CREATE TABLE cards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    keyword_id BIGINT NOT NULL,
    title VARCHAR(20) NOT NULL,
    best_level SMALLINT NOT NULL DEFAULT 0,
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_cards_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_cards_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cards_keyword FOREIGN KEY (keyword_id) REFERENCES keywords(id) ON DELETE RESTRICT
);

-- Partial Index: 삭제된 카드는 조회 제외
CREATE INDEX idx_cards_user_category ON cards(user_id, category_id, keyword_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cards_user_created ON cards(user_id, created_at DESC) WHERE deleted_at IS NULL;


-- 2. card_attempts 테이블
CREATE TABLE card_attempts (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL,
    attempt_no SMALLINT NOT NULL,
    audio_url VARCHAR(500),
    duration_seconds INT,
    stt_text TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message VARCHAR(500),
    CONSTRAINT fk_attempts_card FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_attempts_card_no ON card_attempts(card_id, attempt_no);
CREATE INDEX idx_attempts_card_created ON card_attempts(card_id, created_at DESC);
CREATE INDEX idx_attempts_status ON card_attempts(status, created_at ASC);


-- 3. card_feedbacks 테이블
CREATE TABLE card_feedbacks (
    attempt_id BIGINT PRIMARY KEY,
    overall_score INT NOT NULL,
    level SMALLINT NOT NULL,
    feedback_json JSONB NOT NULL,
    model_version VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_feedbacks_attempt FOREIGN KEY (attempt_id) REFERENCES card_attempts(id) ON DELETE CASCADE
);
