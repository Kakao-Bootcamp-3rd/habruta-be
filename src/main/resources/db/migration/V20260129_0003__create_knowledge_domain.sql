-- 1. categories 테이블
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uk_categories_name ON categories(name);


-- 2. keywords 테이블
CREATE TABLE keywords (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_keywords_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uk_keywords_category_name ON keywords(category_id, name);


-- 3. knowledge_base 테이블 (업로드해주신 V002 내용 반영)
CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    keyword_id BIGINT,
    content TEXT NOT NULL,
    embedding VECTOR(1024), -- 1024차원 벡터
    content_hash CHAR(64) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- FK: 키워드 삭제 시 지식은 유지하되 연결만 끊음 (SET NULL)
    CONSTRAINT fk_kb_keyword FOREIGN KEY (keyword_id) REFERENCES keywords(id) ON DELETE SET NULL
);

-- 중복 방지 인덱스
CREATE UNIQUE INDEX uk_kb_content_hash ON knowledge_base(content_hash);

-- HNSW 인덱스 (벡터 검색 최적화)
CREATE INDEX idx_kb_embedding ON knowledge_base
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE embedding IS NOT NULL;

-- 활성 지식 필터링 인덱스
CREATE INDEX idx_kb_keyword_active ON knowledge_base(keyword_id)
    WHERE is_active = TRUE;

CREATE INDEX idx_kb_created_at ON knowledge_base(created_at DESC);

-- 코멘트 (문서화)
COMMENT ON TABLE knowledge_base IS 'RAG 시스템용 벡터 지식 베이스';
COMMENT ON COLUMN knowledge_base.embedding IS '1024차원 벡터 (OpenAI text-embedding-3-small). 코사인 유사도 검색용.';
