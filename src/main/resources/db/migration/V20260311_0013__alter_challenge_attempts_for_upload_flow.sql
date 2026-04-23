-- challenge_attempts 업로드 플로우 개선
-- 1. audio_url → audio_key 컬럼 rename (공개 URL 대신 S3 object key 저장)
-- 2. duration_seconds 컬럼 추가 (upload-complete 시 클라이언트가 전달)
-- 3. status 기본값 UPLOADED → PENDING 변경 (attempt 생성 직후는 업로드 대기 상태)

ALTER TABLE challenge_attempts
    RENAME COLUMN audio_url TO audio_key;

ALTER TABLE challenge_attempts
    ADD COLUMN duration_seconds INTEGER;

ALTER TABLE challenge_attempts
    ALTER COLUMN status SET DEFAULT 'PENDING';