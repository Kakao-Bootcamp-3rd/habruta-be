package com.imyme.mine.domain.knowledge.service;

import com.imyme.mine.domain.knowledge.entity.KnowledgeBase;
import com.imyme.mine.domain.knowledge.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 지식 베이스 서비스
 * - AI 피드백 채점 기준(모범답안) 제공
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 키워드별 모범답안 조회 (캐싱 적용)
     * - TTL: 2시간 (RedisConfig에서 설정)
     * - sync=true: Cache Stampede 방지
     * - 용도: Solo/PvP AI 피드백 채점 기준 (Criteria)
     *
     * @param keywordId 키워드 ID
     * @return 해당 키워드의 활성 지식 베이스 콘텐츠 목록 (모범답안)
     */
    @Cacheable(value = "keywords:criteria", key = "#keywordId", sync = true)
    public List<String> getModelAnswersByKeyword(Long keywordId) {
        return knowledgeBaseRepository.findByKeywordId(keywordId)
            .stream()
            .filter(KnowledgeBase::getIsActive)
            .map(KnowledgeBase::getContent)
            .toList();
    }
}