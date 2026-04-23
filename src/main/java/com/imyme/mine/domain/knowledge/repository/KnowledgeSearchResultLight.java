package com.imyme.mine.domain.knowledge.repository;

/**
 * 벡터 유사도 검색 경량 결과 Projection (embedding 제외)
 * - evaluateCandidate()에서 content, distance, id만 사용하므로 embedding 전송 불필요
 * - 결과 25건 기준 DB→앱 전송 payload 132KB → 29KB (77% 감소)
 */
public interface KnowledgeSearchResultLight {

    Long getId();

    Long getKeywordId();

    String getContent();

    String getContentHash();

    Double getDistance();
}