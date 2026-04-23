package com.imyme.mine.domain.knowledge.repository;

import java.time.LocalDateTime;

/**
 * 벡터 유사도 검색 결과 매핑용 인터페이스 (Interface Projection)
 *
 * <h3>왜 Object[] 대신 Interface를 사용하나요?</h3>
 * <ul>
 *   <li><b>타입 안전성:</b> {@code Object[] row} 배열 인덱스 실수를 컴파일 단계에서 방지</li>
 *   <li><b>가독성:</b> {@code result.getDistance()} 처럼 직관적으로 접근 가능</li>
 *   <li><b>유지보수성:</b> 쿼리 컬럼 순서 변경 시 배열 인덱스 전부 수정할 필요 없음</li>
 * </ul>
 *
 * <h3>동작 원리</h3>
 * <p>Spring Data JPA는 Native Query의 AS 별칭과 인터페이스의 getter 이름을 매칭합니다.</p>
 * <pre>{@code
 * SELECT kb.id AS id,           -- getId()와 매칭
 *        kb.keyword_id AS keywordId,  -- getKeywordId()와 매칭
 *        (kb.embedding <=> ...) AS distance  -- getDistance()와 매칭
 * }</pre>
 *
 * <h3>주의사항</h3>
 * <ul>
 *   <li>Getter 이름은 반드시 {@code get + 필드명(첫글자 대문자)} 형식이어야 합니다</li>
 *   <li>SQL의 AS 별칭은 camelCase로 작성해야 합니다 (PostgreSQL은 기본적으로 소문자 변환)</li>
 *   <li>인터페이스는 추상 메서드만 선언하고, JPA가 런타임에 프록시 객체를 생성합니다</li>
 * </ul>
 *
 * @see KnowledgeBaseRepository#findSimilarKnowledgeByVector
 * @see KnowledgeBaseRepository#findSimilarKnowledgeByKeyword
 */
public interface KnowledgeSearchResult {

    // 지식 베이스 ID
    Long getId();

    // 연관 키워드 ID (nullable)
    Long getKeywordId();

    // 지식 콘텐츠 원문
    String getContent();

    /**
     * 벡터 임베딩 (1024차원)
     * <p>성능 고려: 필요 없으면 쿼리에서 제외 가능 (데이터가 커서 조회 성능 영향)</p>
     */
    String getEmbedding();

    // 콘텐츠 해시 (SHA-256)
    String getContentHash();

    //  활성화 여부
    Boolean getIsActive();

    // 생성 일시
    LocalDateTime getCreatedAt();

    // 수정 일시
    LocalDateTime getUpdatedAt();

    /**
     * 벡터 코사인 거리 (Cosine Distance)
     * <p><b>거리 해석:</b></p>
     * <ul>
     *   <li>0.0 = 완전히 동일한 의미</li>
     *   <li>0.0 ~ 0.3 = 매우 유사</li>
     *   <li>0.3 ~ 0.7 = 중간 정도 유사</li>
     *   <li>0.7 ~ 2.0 = 유사도 낮음</li>
     * </ul>
     * <p><b>주의:</b> 값이 작을수록 유사하므로, ORDER BY distance ASC로 정렬해야 합니다.</p>
     */
    Double getDistance();
}
