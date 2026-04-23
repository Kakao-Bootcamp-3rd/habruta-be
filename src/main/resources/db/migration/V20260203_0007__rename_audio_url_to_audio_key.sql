-- card_attempts.audio_url → audio_key (objectKey만 저장)
ALTER TABLE card_attempts RENAME COLUMN audio_url TO audio_key;

-- 기존 데이터 백필: 전체 S3 URL → objectKey만 추출
-- 패턴: https://{bucket}.s3.{region}.amazonaws.com/{objectKey}
-- ".amazonaws.com/" 이후의 경로가 objectKey
UPDATE card_attempts
SET audio_key = substring(audio_key FROM position('.amazonaws.com/' IN audio_key) + 15)
WHERE audio_key LIKE 'https://%';