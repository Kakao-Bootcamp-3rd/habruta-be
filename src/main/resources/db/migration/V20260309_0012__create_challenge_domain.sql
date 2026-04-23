-- =========================================================================
-- Challenge Domain Schema
-- =========================================================================
-- 순환 FK 처리 순서:
--   1. challenges (best_submission_id FK 제외)
--   2. challenge_attempts (challenges FK 포함)
--   3. ALTER TABLE challenges ADD FK best_submission_id
--   4. challenge_results
--   5. challenge_rankings


-- -------------------------------------------------------------------------
-- 15. challenges (일일 챌린지 마스터)
-- -------------------------------------------------------------------------
CREATE TABLE challenges (
    id                  BIGSERIAL    PRIMARY KEY,
    keyword_id          BIGINT       NOT NULL,
    keyword_text        VARCHAR(50)  NOT NULL,
    challenge_date      DATE         NOT NULL,
    start_at            TIMESTAMPTZ  NOT NULL,
    end_at              TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    -- best_submission_id FK: challenge_attempts 테이블 생성 후 ALTER TABLE로 추가
    best_submission_id  BIGINT,
    result_summary_json JSONB,
    participant_count   INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_challenges_date
        UNIQUE (challenge_date),
    CONSTRAINT fk_challenges_keyword
        FOREIGN KEY (keyword_id) REFERENCES keywords (id) ON DELETE RESTRICT
);

-- [지난 챌린지 목록] status 조건 필터링 후 challenge_date 정렬 최적화
CREATE INDEX idx_challenges_completed_list ON challenges (status, challenge_date DESC);


-- -------------------------------------------------------------------------
-- 16. challenge_attempts (챌린지 도전 기록)
-- -------------------------------------------------------------------------
CREATE TABLE challenge_attempts (
    id           BIGSERIAL    PRIMARY KEY,
    challenge_id BIGINT       NOT NULL,
    user_id      BIGINT,                        -- 탈퇴 시 NULL 보존
    audio_url    VARCHAR(500),
    stt_text     TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    submitted_at TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ,

    CONSTRAINT uk_attempts_challenge_user
        UNIQUE (challenge_id, user_id),
    CONSTRAINT fk_attempts_challenge
        FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- [완료된 도전 목록] 분석 실패·대기 행을 인덱스에서 제외하는 부분 인덱스
CREATE INDEX idx_attempts_challenge_completed
    ON challenge_attempts (challenge_id, submitted_at DESC)
    WHERE status = 'COMPLETED';


-- -------------------------------------------------------------------------
-- challenges.best_submission_id FK (순환 참조 해소 후 추가)
-- -------------------------------------------------------------------------
ALTER TABLE challenges
    ADD CONSTRAINT fk_challenges_best_submission
        FOREIGN KEY (best_submission_id)
            REFERENCES challenge_attempts (id) ON DELETE SET NULL;


-- -------------------------------------------------------------------------
-- 17. challenge_results (챌린지 AI 채점 결과)
-- -------------------------------------------------------------------------
CREATE TABLE challenge_results (
    attempt_id     BIGINT       PRIMARY KEY,
    score          INT          NOT NULL,
    feedback_json  JSONB        NOT NULL,
    model_version  VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_results_attempt
        FOREIGN KEY (attempt_id) REFERENCES challenge_attempts (id) ON DELETE CASCADE
);


-- -------------------------------------------------------------------------
-- 18. challenge_rankings (챌린지 최종 랭킹 스냅샷 / 명예의 전당)
-- -------------------------------------------------------------------------
CREATE TABLE challenge_rankings (
    id                     BIGSERIAL    PRIMARY KEY,
    challenge_id           BIGINT       NOT NULL,
    user_id                BIGINT,                    -- 탈퇴 시 NULL
    user_nickname          VARCHAR(20)  NOT NULL,     -- 스냅샷
    user_profile_image_url VARCHAR(500),              -- 스냅샷
    rank_no                INT          NOT NULL,
    score                  INT          NOT NULL,
    attempt_id             BIGINT,                    -- 탈퇴·삭제 시 NULL
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_rankings_challenge
        FOREIGN KEY (challenge_id) REFERENCES challenges (id) ON DELETE CASCADE,
    CONSTRAINT fk_rankings_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_rankings_attempt
        FOREIGN KEY (attempt_id) REFERENCES challenge_attempts (id) ON DELETE SET NULL
);

-- user_id 가 NULL인 경우 PostgreSQL은 NULL ≠ NULL 처리 → 탈퇴 유저 다수 레코드 공존 허용
CREATE UNIQUE INDEX uk_rankings_challenge_user
    ON challenge_rankings (challenge_id, user_id);

-- Index-Only Scan: Heap 접근 없이 랭킹 목록 반환 (커버링 인덱스)
CREATE INDEX idx_rankings_view
    ON challenge_rankings (challenge_id, rank_no ASC)
    INCLUDE (user_nickname, user_profile_image_url, score);