-- 이미 knowledge_base 테이블이 존재하는 상태에서 컬럼과 기능을 추가하는 마이그레이션

-- 1. 컬럼 추가 (Add Column)
-- 기존 테이블에 search_vector 컬럼을 추가합니다. 초기값은 NULL입니다.
ALTER TABLE knowledge_base
ADD COLUMN search_vector tsvector;

-- 2. GIN 인덱스 생성 (Create Index)
-- 검색 속도 향상을 위한 역인덱스(Inverted Index) 생성
CREATE INDEX idx_kb_search_vector ON knowledge_base USING GIN(search_vector);

-- 3. 트리거 함수 정의 (Create Function)
-- 키워드 ID를 기반으로 search_vector를 자동 생성/갱신하는 함수
CREATE OR REPLACE FUNCTION update_kb_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    -- keyword_id가 존재하면 keywords 테이블에서 이름을 조회하여 벡터화
    IF NEW.keyword_id IS NOT NULL THEN
        -- 서브쿼리를 사용하여 keywords 테이블의 name을 조회
        -- 'simple': 형태소 분석 없이 띄어쓰기 단위로 토큰화 (한/영 혼용 시 유리)
        -- 'A': 가장 높은 가중치 부여 (검색 시 우선순위)
        NEW.search_vector := (
            SELECT setweight(to_tsvector('simple', name), 'A')
            FROM keywords
            WHERE id = NEW.keyword_id
        );
    ELSE
        NEW.search_vector := NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 4. 트리거 생성 (Create Trigger)
-- INSERT 또는 keyword_id가 변경되는 UPDATE 시에만 동작
CREATE TRIGGER trg_update_kb_search_vector
BEFORE INSERT OR UPDATE OF keyword_id ON knowledge_base
FOR EACH ROW
EXECUTE FUNCTION update_kb_search_vector();

-- 5. 기존 데이터 마이그레이션 (Backfill Data)
-- 컬럼 추가 전 이미 저장되어 있던 데이터들에 대해 search_vector 값을 채워 넣습니다.
UPDATE knowledge_base kb
SET search_vector = setweight(to_tsvector('simple', k.name), 'A')
FROM keywords k
WHERE kb.keyword_id = k.id;
