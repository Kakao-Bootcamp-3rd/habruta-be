package com.imyme.mine.domain.challenge.service;

import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.knowledge.repository.KnowledgeBaseRepository;
import com.imyme.mine.global.config.ChallengeMqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 챌린지 토너먼트 랭킹 초기화 서비스
 *
 * <p>개별 STT가 모두 완료({@code pending_count == 0})된 시점에
 * {@link ChallengeAsyncService}에서 호출됨.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>Redis Hash {@code challenge:{id}:participants}에서 전체 참가자 조회</li>
 *   <li>Challenge → Keyword → KnowledgeBase 조회 후 Rubric을 Redis에 저장</li>
 *   <li>참가자별 개별 leaf 노드 생성:
 *       {@code pairs:job:{id}:level:0:node:{i} = [attemptId]}</li>
 *   <li>홀수일 경우 마지막 노드를 {@code level:1:node:{n/2}}로 bye 복사</li>
 *   <li>2개씩 짝지어 {@code challenge.pairs.eval} 큐로 토너먼트 미션 발행</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingInitService {

    private static final String REDIS_PARTICIPANTS_KEY = "challenge:%d:participants";
    private static final String REDIS_PAIRS_NODE_KEY   = "pairs:job:%d:level:%d:node:%d";
    private static final String REDIS_RUBRIC_KEY       = "knowledge:%d:rubric";
    private static final Duration PAIRS_TTL  = Duration.ofHours(2);
    private static final Duration RUBRIC_TTL = Duration.ofHours(4);

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ChallengeMqProperties mqProperties;
    private final ChallengeRepository challengeRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 토너먼트 랭킹 초기화
     *
     * @param challengeId 완료된 챌린지 ID
     */
    @Transactional(readOnly = true)
    public void initRanking(Long challengeId) {
        String participantsKey = String.format(REDIS_PARTICIPANTS_KEY, challengeId);

        // Hash 전체 조회 (field=attemptId string, value=JSON string)
        Map<Object, Object> participantMap = stringRedisTemplate.opsForHash().entries(participantsKey);

        if (participantMap == null || participantMap.isEmpty()) {
            log.warn("[RankingInit] 참가자 없음 (모두 FAIL?): challengeId={}", challengeId);
            return;
        }

        // attemptId 기준 오름차순 정렬 (결정론적 페어링)
        List<Long> attemptIds = participantMap.keySet().stream()
                .map(k -> Long.parseLong(k.toString()))
                .sorted()
                .collect(Collectors.toList());

        // Challenge → Keyword → KnowledgeBase 조회 (rubric + knowledge_id)
        Long knowledgeId = resolveAndStoreRubric(challengeId);

        int n = attemptIds.size();
        String jobId = String.valueOf(challengeId);

        // 참가자별 개별 leaf 노드 생성: pairs:job:{id}:level:0:node:{i}
        for (int i = 0; i < n; i++) {
            String nodeKey = String.format(REDIS_PAIRS_NODE_KEY, challengeId, 0, i);
            stringRedisTemplate.opsForList().rightPush(nodeKey, String.valueOf(attemptIds.get(i)));
            stringRedisTemplate.expire(nodeKey, PAIRS_TTL);
        }

        // 단독 참가자: array_b 빈 배열로 pairs.eval 발행 (AI가 단독 처리 후 final.done 발행)
        if (n == 1) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("job_id", jobId);
            payload.put("knowledgeBase_id", String.valueOf(knowledgeId));
            payload.put("level", 0);
            payload.put("array_a", List.of(String.valueOf(attemptIds.get(0))));
            payload.put("array_b", List.of());
            payload.put("target_count", 1);
            payload.put("expected_count", 1);
            rabbitTemplate.convertAndSend(
                    mqProperties.getExchange(),
                    mqProperties.getQueue().getPairsEval(),
                    payload
            );
            log.info("[RankingInit] 단독 참가자 pairs.eval 발행: challengeId={}, attemptId={}",
                    challengeId, attemptIds.get(0));
            return;
        }

        // 홀수 bye 처리: 마지막 노드를 level:1:node:{n/2}로 복사 + pairs.eval 발행 (array_b=[])
        // (AI가 생성할 level:1 페어 결과 노드들은 node:0 ~ node:(n/2-1), bye는 그 다음)
        if (n % 2 == 1) {
            Long byeAttemptId = attemptIds.get(n - 1);
            int byeNodeIdx = n / 2;
            String byeKey = String.format(REDIS_PAIRS_NODE_KEY, challengeId, 1, byeNodeIdx);
            stringRedisTemplate.opsForList().rightPush(byeKey, String.valueOf(byeAttemptId));
            stringRedisTemplate.expire(byeKey, PAIRS_TTL);
            log.info("[RankingInit] bye 처리: challengeId={}, byeAttemptId={}, level:1:node:{}",
                    challengeId, byeAttemptId, byeNodeIdx);

            Map<String, Object> byePayload = new HashMap<>();
            byePayload.put("job_id", jobId);
            byePayload.put("knowledgeBase_id", String.valueOf(knowledgeId));
            byePayload.put("level", 0);
            byePayload.put("array_a", List.of(String.valueOf(byeAttemptId)));
            byePayload.put("array_b", List.of());
            byePayload.put("target_count", n);
            byePayload.put("expected_count", n);
            rabbitTemplate.convertAndSend(
                    mqProperties.getExchange(),
                    mqProperties.getQueue().getPairsEval(),
                    byePayload
            );
            log.info("[RankingInit] bye pairs.eval 발행: challengeId={}, byeAttemptId={}", challengeId, byeAttemptId);
        }

        // 2개씩 짝지어 challenge.pairs.eval 발행
        int pairCount = 0;
        for (int i = 0; i + 1 < n; i += 2) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("job_id", jobId);
            payload.put("knowledgeBase_id", String.valueOf(knowledgeId));
            payload.put("level", 0);
            payload.put("array_a", List.of(String.valueOf(attemptIds.get(i))));
            payload.put("array_b", List.of(String.valueOf(attemptIds.get(i + 1))));
            payload.put("target_count", n);
            payload.put("expected_count", n);

            rabbitTemplate.convertAndSend(
                    mqProperties.getExchange(),
                    mqProperties.getQueue().getPairsEval(),
                    payload
            );
            pairCount++;
        }

        log.info("[RankingInit] 토너먼트 초기화 완료: challengeId={}, 참가자={}, 페어={}, knowledgeId={}",
                challengeId, n, pairCount, knowledgeId);
    }

    /**
     * Challenge → Keyword → KnowledgeBase 경로로 knowledge_id를 조회하고,
     * rubric을 Redis에 저장한 뒤 knowledge_id를 반환.
     *
     * @return KnowledgeBase ID, 없으면 null
     */
    private Long resolveAndStoreRubric(Long challengeId) {
        try {
            return challengeRepository.findByIdWithKeyword(challengeId)
                    .flatMap(challenge -> knowledgeBaseRepository
                            .findFirstByKeywordId(challenge.getKeyword().getId()))
                    .map(kb -> {
                        String rubricKey = String.format(REDIS_RUBRIC_KEY, kb.getId());
                        stringRedisTemplate.opsForValue().set(rubricKey, kb.getContent(), RUBRIC_TTL);
                        log.info("[RankingInit] rubric 저장: knowledgeId={}", kb.getId());
                        return kb.getId();
                    })
                    .orElseGet(() -> {
                        log.warn("[RankingInit] KnowledgeBase 없음: challengeId={}", challengeId);
                        return null;
                    });
        } catch (Exception e) {
            log.error("[RankingInit] rubric 조회/저장 실패: challengeId={}", challengeId, e);
            return null;
        }
    }
}