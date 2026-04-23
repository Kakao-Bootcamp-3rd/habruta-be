-- 1. pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 금지어 테이블 (마스터 데이터)
CREATE TABLE forbidden_words (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('COMMON', 'NICKNAME', 'ROOM_NAME')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_forbidden_word ON forbidden_words(word);
CREATE INDEX idx_forbidden_type ON forbidden_words(type);
