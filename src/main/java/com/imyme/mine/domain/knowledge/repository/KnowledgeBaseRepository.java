package com.imyme.mine.domain.knowledge.repository;

import com.imyme.mine.domain.knowledge.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 지식 베이스 리포지토리
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    // 콘텐츠 해시 존재 여부 확인
    boolean existsByContentHash(String contentHash);

    // 여러 콘텐츠 해시 일괄 조회 (N+1 방지용)
    @Query("SELECT kb.contentHash FROM KnowledgeBase kb WHERE kb.contentHash IN :hashes")
    Set<String> findContentHashesByHashIn(@Param("hashes") Set<String> hashes);

    // 콘텐츠 해시로 지식 조회
    Optional<KnowledgeBase> findByContentHash(String contentHash);

    // 임베딩 미생성 활성 지식 조회
    @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.isActive = true AND kb.embedding IS NULL")
    List<KnowledgeBase> findAllWithoutEmbedding();

    // 키워드 ID로 활성 지식 조회
    @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.keyword.id = :keywordId AND kb.isActive = true")
    List<KnowledgeBase> findByKeywordId(@Param("keywordId") Long keywordId);

    // 키워드 ID로 단건 조회 (챌린지 rubric 조회용 — keyword:knowledge_base 1:1 관계 전제)
    @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.keyword.id = :keywordId AND kb.isActive = true")
    Optional<KnowledgeBase> findFirstByKeywordId(@Param("keywordId") Long keywordId);

    /**
     * 전체 벡터 유사도 검색 (Interface Projection 방식)
     *
     * <h3>사용 시나리오</h3>
     * <ul>
     *   <li>일반 질의응답: 사용자 질문과 유사한 지식 검색</li>
     *   <li>문서 요약: 관련 있는 지식들을 모아서 요약</li>
     * </ul>
     *
     * <h3>pgvector 연산자 설명</h3>
     * <ul>
     *   <li><b>{@code <=>}</b>: 코사인 거리 연산자 (Cosine Distance)</li>
     *   <li><b>{@code CAST(:param AS vector)}</b>: 문자열 파라미터를 PostgreSQL vector 타입으로 변환</li>
     *   <li>거리 값이 0에 가까울수록 유사 (0 = 동일, 2 = 정반대)</li>
     * </ul>
     *
     * <h3>HNSW 인덱스 활용</h3>
     * <p>이 쿼리는 {@code idx_kb_embedding} HNSW 인덱스를 자동으로 사용하여 빠른 근사 검색을 수행합니다.</p>
     * <p>마이그레이션 파일(V20260129_0003)에서 생성된 인덱스 설정:</p>
     * <ul>
     *   <li>m=16: 그래프 연결 수 (메모리 vs 정확도 트레이드오프)</li>
     *   <li>ef_construction=64: 인덱스 구축 시 탐색 범위 (높을수록 정확하지만 느림)</li>
     * </ul>
     *
     * @param queryEmbedding OpenAI API로부터 받은 1024차원 벡터를 문자열로 변환한 값
     *                       형식: "[0.1, -0.2, 0.5, ...]" (Service 계층에서 변환 필요)
     * @param limit 반환할 최대 결과 수 (Top-K)
     * @return 유사도 높은 순으로 정렬된 지식 목록 (거리 정보 포함)
     * @see KnowledgeSearchResult
     */
    @Query(value = """
        SELECT kb.id AS id,
               kb.keyword_id AS keywordId,
               kb.content AS content,
               kb.embedding AS embedding,
               kb.content_hash AS contentHash,
               kb.is_active AS isActive,
               kb.created_at AS createdAt,
               kb.updated_at AS updatedAt,
               (kb.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
        FROM knowledge_base kb
        WHERE kb.is_active = true
          AND kb.embedding IS NOT NULL
        ORDER BY distance ASC

        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeSearchResult> findSimilarKnowledgeByVector(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("limit") int limit
    );

    /**
     * 키워드별 벡터 유사도 검색 (Interface Projection 방식)
     *
     * <h3>사용 시나리오</h3>
     * <ul>
     *   <li>키워드별 맞춤형 피드백 생성: 사용자가 학습 중인 특정 키워드에 대한 지식만 검색</li>
     *   <li>카테고리 필터링: 특정 도메인(Java, Spring 등)에 한정된 검색</li>
     * </ul>
     *
     * <h3>전체 검색 대비 차이점</h3>
     * <p>{@link #findSimilarKnowledgeByVector}는 전체 지식을 대상으로 검색하지만,
     * 이 메서드는 WHERE 절에 {@code kb.keyword_id = :keywordId} 조건을 추가하여
     * 특정 키워드에 연관된 지식만 검색합니다.</p>
     *
     * <h3>성능 최적화</h3>
     * <p>WHERE 절의 keyword_id 필터링은 HNSW 인덱스 검색 <b>이후</b>에 적용됩니다.
     * 키워드별 검색이 빈번하다면, 키워드별로 별도 테이블을 구성하거나
     * Partial Index 생성을 고려할 수 있습니다.</p>
     *
     * @param queryEmbedding OpenAI API로부터 받은 1024차원 벡터 문자열
     * @param keywordId 필터링할 키워드 ID
     * @param limit 반환할 최대 결과 수
     * @return 해당 키워드에 속한 지식 중 유사도 높은 순으로 정렬된 목록
     * @see KnowledgeSearchResult
     */
    @Query(value = """
        SELECT kb.id AS id,
               kb.keyword_id AS keywordId,
               kb.content AS content,
               kb.embedding AS embedding,
               kb.content_hash AS contentHash,
               kb.is_active AS isActive,
               kb.created_at AS createdAt,
               kb.updated_at AS updatedAt,
               (kb.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
        FROM knowledge_base kb
        WHERE kb.is_active = true
          AND kb.embedding IS NOT NULL
          AND kb.keyword_id = :keywordId
        ORDER BY distance ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeSearchResult> findSimilarKnowledgeByKeyword(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("keywordId") Long keywordId,
        @Param("limit") int limit
    );

    /**
     * 키워드별 벡터 유사도 검색 - 경량 버전 (embedding 제외)
     * - evaluateCandidate()에서 embedding 미사용 → SELECT에서 제거
     * - 결과 25건 기준 전송 payload 132KB → 29KB (77% 감소)
     */
    @Query(value = """
        SELECT kb.id          AS id,
               kb.keyword_id  AS keywordId,
               kb.content     AS content,
               kb.content_hash AS contentHash,
               (kb.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
        FROM knowledge_base kb
        WHERE kb.is_active = true
          AND kb.embedding IS NOT NULL
          AND kb.keyword_id = :keywordId
        ORDER BY distance ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeSearchResultLight> findSimilarKnowledgeByKeywordLight(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("keywordId") Long keywordId,
        @Param("limit") int limit
    );

    /**
     * 모든 활성 지식 조회 (최신순)
     * - 목적: 관리자 페이지에서 지식 목록 표시
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.isActive = true ORDER BY kb.createdAt DESC")
    List<KnowledgeBase> findAllActiveOrderByCreatedAtDesc();

    /**
     * 활성 지식 개수 조회
     * - 목적: 대시보드 통계
     */
    @Query("SELECT COUNT(kb) FROM KnowledgeBase kb WHERE kb.isActive = true")
    long countActiveKnowledge();

    /**
     * 임베딩 미생성 지식 개수 조회
     * - 목적: 배치 작업 모니터링
     */
    @Query("SELECT COUNT(kb) FROM KnowledgeBase kb WHERE kb.isActive = true AND kb.embedding IS NULL")
    long countWithoutEmbedding();
}
