package com.imyme.mine.domain.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.ai.client.AiServerClient;
import com.imyme.mine.domain.ai.dto.knowledge.*;
import com.imyme.mine.domain.card.entity.CardFeedback;
import com.imyme.mine.domain.card.repository.CardFeedbackRepository;
import com.imyme.mine.domain.knowledge.entity.KnowledgeBase;
import com.imyme.mine.domain.knowledge.repository.KnowledgeBaseRepository;
import com.imyme.mine.domain.knowledge.repository.KnowledgeSearchResult;
import com.imyme.mine.domain.knowledge.repository.KnowledgeSearchResultLight;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.learning.dto.KnowledgeBatchResult;
import com.imyme.mine.global.config.KnowledgeProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Knowledge 배치 작업 서비스
 * - 매일 자정 실행되는 Knowledge 업데이트 파이프라인
 * - SHA-256 해시 기반 중복 방지로 OpenAI API 비용 최소화
 * - 트랜잭션은 DB 저장 메서드에만 적용 (HTTP 호출 시 트랜잭션 미사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBatchService {

    private final CardFeedbackRepository feedbackRepository;
    private final KnowledgeBaseRepository knowledgeRepository;
    private final KeywordRepository keywordRepository;
    private final AiServerClient aiServerClient;
    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;  // 재사용을 위해 주입

    /**
     * 전체 키워드에 대해 Knowledge 배치 실행 (매일 자정)
     * - @Transactional 없음: HTTP 호출과 Thread.sleep이 포함되어 있어 장시간 트랜잭션 방지
     */
    public KnowledgeBatchResult executeDaily() {
        log.info("=== Knowledge 일일 배치 시작 ===");

        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        KnowledgeBatchResult.Builder resultBuilder = KnowledgeBatchResult.builder()
            .startTime(startTime);

        // 활성 키워드 조회
        List<Keyword> keywords = getActiveKeywords();
        resultBuilder.totalKeywords(keywords.size());

        log.info("처리 대상 키워드: {}개", keywords.size());

        int successCount = 0;
        int failedCount = 0;
        int totalFeedbacks = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int ignoredCount = 0;

        // 각 키워드별 처리
        for (Keyword keyword : keywords) {
            try {
                log.info("키워드 처리 시작: {} (ID: {})", keyword.getName(), keyword.getId());

                KnowledgeBatchResult keywordResult = processKeyword(keyword.getId(), yesterday, today);

                successCount++;
                totalFeedbacks += keywordResult.totalFeedbacks();
                createdCount += keywordResult.createdKnowledge();
                updatedCount += keywordResult.updatedKnowledge();
                ignoredCount += keywordResult.ignoredCandidates();

                log.info("키워드 처리 완료: {} - 피드백 {}, 생성 {}, 업데이트 {}, 무시 {}",
                    keyword.getName(),
                    keywordResult.totalFeedbacks(),
                    keywordResult.createdKnowledge(),
                    keywordResult.updatedKnowledge(),
                    keywordResult.ignoredCandidates());

                // 키워드 간 지연 (AI 서버 부하 방지)
                if (properties.getKeywordDelayMs() > 0) {
                    Thread.sleep(properties.getKeywordDelayMs());
                }

            } catch (Exception e) {
                failedCount++;
                String errorMsg = String.format("키워드 처리 실패: %s (ID: %d) - %s",
                    keyword.getName(), keyword.getId(), e.getMessage());
                log.error(errorMsg, e);
                resultBuilder.addError(errorMsg);
            }
        }

        LocalDateTime endTime = LocalDateTime.now();

        KnowledgeBatchResult result = resultBuilder
            .endTime(endTime)
            .successKeywords(successCount)
            .failedKeywords(failedCount)
            .totalFeedbacks(totalFeedbacks)
            .createdKnowledge(createdCount)
            .updatedKnowledge(updatedCount)
            .ignoredCandidates(ignoredCount)
            .build();

        log.info("=== Knowledge 일일 배치 완료 ===");
        log.info("처리 시간: {}초", java.time.Duration.between(startTime, endTime).getSeconds());
        log.info("성공: {}, 실패: {}, 총 피드백: {}, 생성: {}, 업데이트: {}, 무시: {}",
            successCount, failedCount, totalFeedbacks, createdCount, updatedCount, ignoredCount);

        return result;
    }

    /**
     * [Admin용] 전체 기간 배치 실행 (오버로딩)
     * - 날짜에 null을 넘겨서 전체 조회가 되도록 함
     */
    public KnowledgeBatchResult processKeyword(Long keywordId) {
        return processKeyword(keywordId, null, null);
    }

    /**
     * [구버전 호환용] 날짜 지정 배치 실행 (오버로딩)
     */
    public KnowledgeBatchResult processKeywordByDateRange(Long keywordId, LocalDateTime start, LocalDateTime end) {
        return processKeyword(keywordId, start, end);
    }

    /**
     * [핵심 로직 통합] 특정 키워드에 대해 Knowledge 배치 실행
     * - startDate, endDate가 있으면 해당 기간만 조회 (일일 배치용)
     * - 날짜가 null이면 전체 조회 (Admin 수동 실행용)
     * - 리팩토링: 174줄 God Method를 8개 메서드로 분리하여 가독성 향상
     */
    public KnowledgeBatchResult processKeyword(Long keywordId, LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime batchStartTime = LocalDateTime.now();
        KnowledgeBatchResult.Builder resultBuilder = KnowledgeBatchResult.builder()
            .startTime(batchStartTime)
            .totalKeywords(1);

        // 1. 키워드 로드
        Keyword keyword = loadKeyword(keywordId);

        // 2~5. 페이지 단위 스트리밍 처리 (OOM 방지)
        int totalFeedbacks = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int ignoredCount = 0;
        int pageNumber = 0;
        final int PAGE_SIZE = properties.getBatchSize();

        while (true) {
            Slice<CardFeedback> slice = loadFeedbackSlice(keywordId, startDate, endDate, pageNumber, PAGE_SIZE);
            List<CardFeedback> feedbacks = slice.getContent();

            if (feedbacks.isEmpty()) {
                break;
            }

            totalFeedbacks += feedbacks.size();
            log.info("페이지 {} 처리 중 - 피드백 {}건", pageNumber, feedbacks.size());

            // 피드백 아이템 추출 (중복 제거 및 해시 계산)
            List<FeedbackItem> feedbackItems = extractFeedbackItems(feedbacks, keyword.getName());
            log.info("중복 제거 후 처리 대상: {}개", feedbackItems.size());

            if (!feedbackItems.isEmpty()) {
                // 배치 생성 및 처리
                List<List<FeedbackItem>> batches = createBatches(feedbackItems);
                BatchProcessingResult pageResult = processBatches(batches, keyword, resultBuilder);
                createdCount += pageResult.createdCount();
                updatedCount += pageResult.updatedCount();
                ignoredCount += pageResult.ignoredCount();
            }

            if (!slice.hasNext()) {
                break;
            }
            pageNumber++;
        }

        resultBuilder.totalFeedbacks(totalFeedbacks);
        BatchProcessingResult processingResult = new BatchProcessingResult(createdCount, updatedCount, ignoredCount);

        return resultBuilder
            .endTime(LocalDateTime.now())
            .successKeywords(1)
            .createdKnowledge(processingResult.createdCount())
            .updatedKnowledge(processingResult.updatedCount())
            .ignoredCandidates(processingResult.ignoredCount())
            .build();
    }

    /**
     * 1단계: 키워드 로드
     */
    private Keyword loadKeyword(Long keywordId) {
        return keywordRepository.findById(keywordId)
            .orElseThrow(() -> new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));
    }

    /**
     * 2단계: 피드백 Slice 조회 (페이지 단위, OOM 방지)
     */
    private Slice<CardFeedback> loadFeedbackSlice(Long keywordId, LocalDateTime startDate, LocalDateTime endDate,
                                                   int pageNumber, int pageSize) {
        PageRequest pageable = PageRequest.of(pageNumber, pageSize);
        if (startDate != null && endDate != null) {
            return feedbackRepository.findByKeywordIdAndCreatedAtBetweenSlice(keywordId, startDate, endDate, pageable);
        } else {
            return feedbackRepository.findByKeywordIdSlice(keywordId, pageable);
        }
    }

    /**
     * 3단계: 피드백 아이템 추출 (중복 제거 및 해시 계산)
     * - 모든 contentHash를 한 번에 조회하여 N+1 쿼리 방지
     */
    private List<FeedbackItem> extractFeedbackItems(List<CardFeedback> feedbacks, String keywordName) {
        // 1) 모든 피드백의 해시 계산
        record FeedbackWithHash(CardFeedback feedback, String text, String hash) {}
        List<FeedbackWithHash> candidates = new ArrayList<>();

        for (CardFeedback feedback : feedbacks) {
            try {
                String text = extractPersonalizedFeedback(feedback.getFeedbackJson());
                log.debug("ID: {}, 추출된 텍스트: {}", feedback.getAttemptId(), text);

                if (text != null && !text.isBlank()) {
                    candidates.add(new FeedbackWithHash(feedback, text, calculateSHA256(text)));
                } else {
                    log.warn("ID: {} - 텍스트 추출 실패 (null 또는 빈 값)", feedback.getAttemptId());
                }
            } catch (Exception e) {
                log.warn("피드백 추출 실패 - attemptId: {}", feedback.getAttemptId(), e);
            }
        }

        // 2) 전체 해시를 1번 쿼리로 일괄 조회
        Set<String> allHashes = candidates.stream()
            .map(FeedbackWithHash::hash)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> existingHashes = knowledgeRepository.findContentHashesByHashIn(allHashes);

        // 3) 메모리에서 중복 필터링
        List<FeedbackItem> feedbackItems = new ArrayList<>();
        for (FeedbackWithHash c : candidates) {
            if (existingHashes.contains(c.hash())) {
                log.debug("이미 존재하는 지식 스킵 - Hash: {}", c.hash());
            } else {
                feedbackItems.add(new FeedbackItem(
                    String.valueOf(c.feedback().getAttemptId()),
                    keywordName,
                    c.text()
                ));
            }
        }

        return feedbackItems;
    }

    /**
     * 4단계: 배치 생성
     */
    private List<List<FeedbackItem>> createBatches(List<FeedbackItem> feedbackItems) {
        List<List<FeedbackItem>> batches = partitionList(feedbackItems, properties.getBatchSize());
        log.info("배치 개수: {} (배치 크기: {})", batches.size(), properties.getBatchSize());
        return batches;
    }

    /**
     * 5단계: 배치 처리 (AI 서버 호출 및 지식 저장)
     */
    private BatchProcessingResult processBatches(
        List<List<FeedbackItem>> batches,
        Keyword keyword,
        KnowledgeBatchResult.Builder resultBuilder
    ) {
        int createdCount = 0;
        int updatedCount = 0;
        int ignoredCount = 0;

        for (int i = 0; i < batches.size(); i++) {
            try {
                List<FeedbackItem> batch = batches.get(i);
                log.info("배치 {}/{} 처리 중 - 항목 수: {}", i + 1, batches.size(), batch.size());

                // 배치를 AI 서버로 전송하여 후보 생성
                List<KnowledgeCandidate> candidates = submitBatchForCandidates(batch, i + 1);

                // 후보가 생성되면 2분 대기 (AI 서버 과부하 방지)
                if (!candidates.isEmpty()) {
                    waitForServerCooldown();
                }

                // 각 후보 평가 및 저장
                CandidateProcessingResult candidateResult = processCandidates(candidates, keyword, resultBuilder);
                createdCount += candidateResult.created();
                updatedCount += candidateResult.updated();
                ignoredCount += candidateResult.ignored();

                // 배치 간 지연
                if (i < batches.size() - 1 && properties.getBatchDelayMs() > 0) {
                    Thread.sleep(properties.getBatchDelayMs());
                }

            } catch (Exception e) {
                log.error("배치 {}/{} 처리 실패", i + 1, batches.size(), e);
                resultBuilder.addError(String.format("배치 %d 처리 실패: %s", i + 1, e.getMessage()));
            }
        }

        return new BatchProcessingResult(createdCount, updatedCount, ignoredCount);
    }

    /**
     * 5-1단계: 배치를 AI 서버로 전송하여 후보 생성
     */
    private List<KnowledgeCandidate> submitBatchForCandidates(List<FeedbackItem> batch, int batchNumber) {
        KnowledgeCandidateBatchRequest request = new KnowledgeCandidateBatchRequest(batch);

        // AI 서버로 보내는 실제 JSON 데이터 로깅
        try {
            String requestBodyJson = objectMapper.writeValueAsString(request);
            log.info(">>> [AI Request Body] Batch {}: {}", batchNumber, requestBodyJson);
        } catch (Exception e) {
            log.warn("요청 데이터 로깅 실패", e);
        }

        // AI 서버 요청
        List<KnowledgeCandidate> candidates = aiServerClient.createKnowledgeCandidatesBatch(request);
        log.info("AI 서버 응답: {} 개 후보 생성", candidates.size());

        return candidates;
    }

    /**
     * 5-2단계: AI 서버 과부하 방지를 위한 대기
     */
    private void waitForServerCooldown() {
        long sleepTime = properties.getIdleSleepMs();
        log.info("🔥 서버 과부하 방지를 위해 {}분간 대기합니다... (현재 시간: {})",
            sleepTime / 60000, LocalDateTime.now());

        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("💤 휴식 끝! 평가 작업을 시작합니다.");
    }

    /**
     * 5-3단계: 후보 평가 및 저장
     */
    private CandidateProcessingResult processCandidates(
        List<KnowledgeCandidate> candidates,
        Keyword keyword,
        KnowledgeBatchResult.Builder resultBuilder
    ) {
        int created = 0;
        int updated = 0;
        int ignored = 0;

        for (KnowledgeCandidate candidate : candidates) {
            try {
                CandidateDecision decision = evaluateCandidate(candidate, keyword.getId());

                if ("UPDATE".equalsIgnoreCase(decision.decision())) {
                    if (decision.targetKnowledgeId() == null) {
                        createKnowledge(keyword, candidate);
                        created++;
                        log.info("새 지식 생성 완료 - feedbackId: {}", candidate.id());
                    } else {
                        updateKnowledge(decision.targetKnowledgeId(), candidate);
                        updated++;
                        log.info("기존 지식 업데이트 완료 - knowledgeId: {}", decision.targetKnowledgeId());
                    }
                } else {
                    ignored++;
                    log.debug("지식 후보 무시 - feedbackId: {}", candidate.id());
                }
            } catch (Exception e) {
                log.error("후보 처리 실패 - feedbackId: {}", candidate.id(), e);
                resultBuilder.addError("후보 처리 실패: " + e.getMessage());
            }
        }

        return new CandidateProcessingResult(created, updated, ignored);
    }

    /**
     * 5-3-1단계: 후보 평가 (유사도 검색 + AI 평가)
     */
    private CandidateDecision evaluateCandidate(KnowledgeCandidate candidate, Long keywordId) {
        String embeddingVector = convertEmbeddingToString(candidate.embedding());

        // 유사 지식 검색 (경량 버전: embedding 제외로 payload 77% 감소)
        List<KnowledgeSearchResultLight> similars = knowledgeRepository
            .findSimilarKnowledgeByKeywordLight(
                embeddingVector,
                keywordId,
                properties.getMaxSimilarCount()
            );

        // 유사도 임계값 필터링
        double threshold = 1.0 - properties.getSimilarityThreshold();
        List<KnowledgeSearchResultLight> filteredSimilars = similars.stream()
            .filter(s -> s.getDistance() <= threshold)
            .collect(Collectors.toList());

        log.debug("유사 지식 검색 결과: {} -> 필터링 후 {}", similars.size(), filteredSimilars.size());

        // AI 평가 요청
        KnowledgeEvaluationRequest evalRequest = buildEvaluationRequest(candidate, filteredSimilars);
        KnowledgeEvaluationResponse.Data evalResult = aiServerClient.evaluateKnowledge(evalRequest);

        log.debug("평가 결과: {} - {}", evalResult.decision(), evalResult.reasoning());

        Long targetKnowledgeId = filteredSimilars.isEmpty() ? null : filteredSimilars.get(0).getId();
        return new CandidateDecision(evalResult.decision(), targetKnowledgeId);
    }

    /**
     * 배치 처리 결과 DTO
     */
    private record BatchProcessingResult(int createdCount, int updatedCount, int ignoredCount) {}

    /**
     * 후보 처리 결과 DTO
     */
    private record CandidateProcessingResult(int created, int updated, int ignored) {}

    /**
     * 후보 평가 결과 DTO
     */
    private record CandidateDecision(String decision, Long targetKnowledgeId) {}

    /**
     * 새 지식 생성 (트랜잭션 적용)
     */
    @Transactional
    public void createKnowledge(Keyword keyword, KnowledgeCandidate candidate) {
        String contentHash = calculateSHA256(candidate.refinedText());
        String embeddingVector = convertEmbeddingToString(candidate.embedding());

        KnowledgeBase knowledge = KnowledgeBase.builder()
            .keyword(keyword)
            .content(candidate.refinedText())
            .embedding(embeddingVector)
            .contentHash(contentHash)
            .isActive(true)
            .build();

        knowledgeRepository.save(knowledge);
        log.debug("지식 생성 완료 - ID: {}", knowledge.getId());
    }

    /**
     * 기존 지식 업데이트 (트랜잭션 적용)
     */
    @Transactional
    public void updateKnowledge(Long knowledgeId, KnowledgeCandidate candidate) {
        KnowledgeBase knowledge = knowledgeRepository.findById(knowledgeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND));

        String newContentHash = calculateSHA256(candidate.refinedText());
        String newEmbedding = convertEmbeddingToString(candidate.embedding());

        knowledge.updateContent(candidate.refinedText(), newContentHash);
        knowledge.updateEmbedding(newEmbedding);

        knowledgeRepository.save(knowledge);
        log.debug("지식 업데이트 완료 - ID: {}", knowledge.getId());
    }

    // ========== Helper Methods ==========

    /**
     * 활성 키워드 조회 (필터링 적용)
     */
    private List<Keyword> getActiveKeywords() {
        List<Keyword> allKeywords = keywordRepository.findAllWithCategoryByIsActive(true);

        // 설정에서 활성화된 키워드 필터링
        if (properties.getEnabledKeywords().isEmpty()) {
            return allKeywords;
        } else {
            Set<Long> enabledIds = new HashSet<>(properties.getEnabledKeywords());
            return allKeywords.stream()
                .filter(k -> enabledIds.contains(k.getId()))
                .collect(Collectors.toList());
        }
    }

    /**
     * 피드백 JSON에서 개인화 피드백 추출
     */
    private String extractPersonalizedFeedback(String feedbackJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(feedbackJson);

            com.fasterxml.jackson.databind.JsonNode feedbackNode = root.path("socratic_feedback");

            if (feedbackNode.isMissingNode() || feedbackNode.isNull()) {
                feedbackNode = root.path("data")
                    .path("feedback_content")
                    .path("socratic_feedback");
            }

            if (feedbackNode.isMissingNode() || feedbackNode.isNull()) {
                return null;
            }

            return feedbackNode.asText();
        } catch (Exception e) {
            log.warn("피드백 JSON 파싱 실패", e);
            return null;
        }
    }

    /**
     * SHA-256 해시 계산
     */
    private String calculateSHA256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 임베딩 벡터를 PostgreSQL vector 형식으로 변환
     */
    private String convertEmbeddingToString(List<Double> embedding) {
        return embedding.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * 평가 요청 DTO 생성
     */
    private KnowledgeEvaluationRequest buildEvaluationRequest(
        KnowledgeCandidate candidate,
        List<KnowledgeSearchResultLight> similars
    ) {
        // EvaluationCandidate(String text, String sourceId)
        EvaluationCandidate evalCandidate = new EvaluationCandidate(
            candidate.refinedText(),  // text
            candidate.id()  // sourceId
        );

        // SimilarKnowledge(String id, String text, Double similarity)
        List<SimilarKnowledge> similarList = similars.stream()
            .map(s -> new SimilarKnowledge(
                String.valueOf(s.getId()),  // String ID로 변환
                s.getContent(),
                1.0 - s.getDistance()  // 거리를 유사도로 변환 (1 - distance)
            ))
            .collect(Collectors.toList());

        return new KnowledgeEvaluationRequest(evalCandidate, similarList);
    }

    /**
     * 리스트를 배치 크기로 분할
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }
}
