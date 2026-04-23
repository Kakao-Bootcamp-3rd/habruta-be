package com.imyme.mine.domain.card.service;

import com.imyme.mine.domain.ai.client.AiServerClient;
import com.imyme.mine.domain.card.dto.AttemptCreateRequest;
import com.imyme.mine.domain.card.dto.AttemptCreateResponse;
import com.imyme.mine.domain.card.dto.AttemptDetailResponse;
import com.imyme.mine.domain.card.dto.AttemptProcessingStep;
import com.imyme.mine.domain.card.dto.UploadCompleteRequest;
import com.imyme.mine.domain.card.dto.UploadCompleteResponse;
import com.imyme.mine.domain.card.dto.ValidatedAttempt;
import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.event.AttemptUploadedEvent;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.entity.CardFeedback;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardFeedbackRepository;
import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.domain.knowledge.repository.KnowledgeBaseRepository;
import com.imyme.mine.domain.knowledge.service.KnowledgeBaseService;
import com.imyme.mine.domain.learning.messaging.SoloMqPublisher;
import com.imyme.mine.domain.learning.messaging.dto.SoloSttRequestDto;
import com.imyme.mine.global.config.AttemptProperties;
import com.imyme.mine.global.config.S3Properties;
import com.imyme.mine.global.config.SoloMqProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptService {

    private final CardRepository cardRepository;
    private final CardAttemptRepository cardAttemptRepository;
    private final CardFeedbackRepository cardFeedbackRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AiServerClient aiServerClient;
    private final ApplicationEventPublisher eventPublisher;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final AttemptProperties attemptProperties;
    private final AttemptUploadService attemptUploadService;
    private final AttemptSttService attemptSttService;
    private final SoloMqProperties soloMqProperties;
    private final SoloMqPublisher soloMqPublisher;

    private static final int MAX_ATTEMPTS_PER_CARD = 5;

    @Transactional
    public AttemptCreateResponse createAttempt(Long userId, Long cardId, AttemptCreateRequest request) {
        log.debug("시도 생성 시작 - userId: {}, cardId: {}", userId, cardId);

        Card card = cardRepository.findByIdAndUserId(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        // FAILED/EXPIRED는 카운트에서 제외 (재시도 가능하도록)
        List<AttemptStatus> excludedStatuses = List.of(AttemptStatus.FAILED, AttemptStatus.EXPIRED);
        long attemptCount = cardAttemptRepository.countByCardIdAndStatusNotIn(card.getId(), excludedStatuses);
        if (attemptCount >= MAX_ATTEMPTS_PER_CARD) {
            throw new BusinessException(ErrorCode.MAX_ATTEMPTS_EXCEEDED);
        }

        Short nextAttemptNo = calculateNextAttemptNo(card.getId());

        CardAttempt attempt = CardAttempt.builder()
            .card(card)
            .attemptNo(nextAttemptNo)
            .status(AttemptStatus.PENDING)
            .durationSeconds(request.durationSeconds())
            .build();

        CardAttempt savedAttempt = cardAttemptRepository.save(attempt);

        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()));

        log.info("시도 생성 완료 - attemptId: {}, attemptNo: {}", savedAttempt.getId(), savedAttempt.getAttemptNo());

        return AttemptCreateResponse.of(savedAttempt, expiresAt);
    }

    /**
     * Solo 시도 상세 조회 (캐싱 적용)
     * - TTL: 7일 (RedisConfig에서 설정)
     * - 조건부 캐싱: COMPLETED 상태일 때만 캐싱 (Immutable 데이터)
     * - PROCESSING/FAILED는 캐싱하지 않음 (폴링 API)
     */
    @Cacheable(value = "ai:feedback:solo", key = "#attemptId",
               condition = "#result != null && #result.status() == 'COMPLETED'")
    @Transactional(readOnly = true)
    public AttemptDetailResponse getAttemptDetail(Long userId, Long cardId, Long attemptId) {
        log.debug("시도 상세 조회 - userId: {}, cardId: {}, attemptId: {}", userId, cardId, attemptId);

        ValidatedAttempt validated = validateAttemptOwnership(userId, cardId, attemptId);

        return buildAttemptDetailResponse(validated.attempt());
    }

    public UploadCompleteResponse uploadComplete(Long userId, Long cardId, Long attemptId, UploadCompleteRequest request) {
        log.debug("업로드 완료 처리 시작 - userId: {}, cardId: {}, attemptId: {}", userId, cardId, attemptId);

        // 1단계: 트랜잭션 내에서 검증 및 상태 업데이트만 수행 (프록시 호출로 @Transactional 보장)
        ValidatedAttempt validated = attemptUploadService.markAttemptAsUploaded(userId, cardId, attemptId, request);
        Card card = validated.card();

        // 2단계: 트랜잭션 외부에서 외부 HTTP 호출 수행
        processAfterUpload(attemptId, card, request.objectKey());

        // 3단계: 최종 상태 조회 및 응답 반환
        CardAttempt finalAttempt = cardAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        log.info("업로드 완료 처리 완료 - attemptId: {}, status: {}", attemptId, finalAttempt.getStatus());

        return UploadCompleteResponse.from(finalAttempt);
    }

    /**
     * 2단계: 트랜잭션 외부에서 STT 처리 및 이벤트 발행
     * - 외부 HTTP 호출이므로 트랜잭션 외부에서 실행
     * - DB 커넥션을 장기간 점유하지 않음
     */
    private void processAfterUpload(Long attemptId, Card card, String objectKey) {
        try {
            log.debug("STT 처리 시작 - attemptId: {}", attemptId);

            // 읽기용 Presigned URL 생성 (AI 서버가 S3에서 다운로드 가능)
            String readPresignedUrl = generateReadPresignedUrl(objectKey);
            log.debug("읽기용 Presigned URL 생성 완료 - attemptId: {}", attemptId);

            if (soloMqProperties.isEnabled()) {
                // MQ 경로: STT 요청을 MQ로 발행 (AI 서버가 비동기 처리)
                log.info("[Solo MQ] STT Request 발행 - attemptId: {}", attemptId);
                SoloSttRequestDto payload = SoloSttRequestDto.builder()
                    .requestId(UUID.randomUUID().toString())
                    .attemptId(attemptId)
                    .userId(card.getUser().getId())
                    .audioUrl(readPresignedUrl)
                    .timestamp(System.currentTimeMillis())
                    .build();
                soloMqPublisher.publishSttRequest(payload);
            } else {
                // HTTP 경로: 기존 동기 STT 호출
                String sttText = aiServerClient.transcribe(readPresignedUrl);
                attemptSttService.recordSttSuccess(attemptId, sttText);
                publishSoloAnalysisEvent(attemptId, card, sttText);
            }

        } catch (BusinessException e) {
            String errorCode = mapSttErrorCode(e);
            attemptSttService.recordSttFailure(attemptId, errorCode);
            log.error("STT 처리 실패 - attemptId: {}, errorCode: {}", attemptId, errorCode);
        } catch (Exception e) {
            attemptSttService.recordSttFailure(attemptId, "UNKNOWN_ERROR");
            log.error("STT 처리 중 예상치 못한 오류 - attemptId: {}", attemptId, e);
        }
    }

    /**
     * Solo 분석 이벤트 발행
     * - Event 기반으로 SoloService와 결합도 감소
     */
    private void publishSoloAnalysisEvent(Long attemptId, Card card, String sttText) {
        try {
            log.info("Solo 분석 이벤트 발행 - attemptId: {}", attemptId);
            Map<String, Object> criteria = resolveCriteria(card);
            List<Map<String, Object>> history = List.of();

            AttemptUploadedEvent event = new AttemptUploadedEvent(
                attemptId,
                card.getUser().getId(),
                card.getId(),
                sttText,
                criteria,
                history
            );

            eventPublisher.publishEvent(event);
            log.info("Solo 분석 이벤트 발행 완료 - attemptId: {}", attemptId);

        } catch (Exception e) {
            // 이벤트 발행 실패해도 STT는 성공했으므로 계속 진행
            log.error("Solo 분석 이벤트 발행 실패 - attemptId: {}", attemptId, e);
            attemptSttService.recordSttFailure(attemptId, "AI_FEEDBACK_FAILED");
        }
    }

    @Transactional
    public void deleteAttempt(Long userId, Long cardId, Long attemptId) {
        log.debug("학습 시도 삭제 시작 - userId: {}, cardId: {}, attemptId: {}", userId, cardId, attemptId);

        CardAttempt attempt = findAttemptWithValidation(userId, cardId, attemptId);

        // 삭제 가능한 상태인지 확인 (PENDING, FAILED, EXPIRED 만 삭제 가능)
        if (isNotDeletable(attempt.getStatus())) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_UPLOADED);
        }

        // 🚨 중요: Hard Delete (DB에서 완전 삭제)
        // 그래야 count가 줄어들어 사용자가 다시 시도할 수 있음
        cardAttemptRepository.delete(attempt);

        log.info("학습 시도 삭제 완료 (Hard Delete) - attemptId: {}", attemptId);
    }

    private CardAttempt findAttemptWithValidation(Long userId, Long cardId, Long attemptId) {
        CardAttempt attempt = cardAttemptRepository.findByIdWithCardAndUser(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        // 카드 일치 여부 및 소유권 확인
        if (!attempt.getCard().getId().equals(cardId) || !attempt.getCard().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_CARD_ATTEMPT_MISMATCH);
        }
        return attempt;
    }

    /**
     * 학습 시도의 소유권 및 관계 검증
     * - 중복 검증 로직 제거를 위한 공통 메서드
     * - Card와 CardAttempt의 일치 여부 확인
     */
    private ValidatedAttempt validateAttemptOwnership(Long userId, Long cardId, Long attemptId) {
        CardAttempt attempt = cardAttemptRepository.findByIdWithCardAndUser(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        if (!attempt.getCard().getId().equals(cardId) || !attempt.getCard().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_CARD_ATTEMPT_MISMATCH);
        }

        return new ValidatedAttempt(attempt.getCard(), attempt);
    }

    private Short calculateNextAttemptNo(Long cardId) {
        Short maxAttemptNo = cardAttemptRepository.findMaxAttemptNoByCardId(cardId);
        return (maxAttemptNo == null) ? 1 : (short) (maxAttemptNo + 1);
    }

    private boolean isNotDeletable(AttemptStatus status) {
        // 진행 중이거나 완료된 건은 결과 보존을 위해 삭제 불가
        return status == AttemptStatus.UPLOADED ||
            status == AttemptStatus.PROCESSING ||
            status == AttemptStatus.COMPLETED;
    }

    @Transactional(readOnly = true)
    private AttemptDetailResponse buildAttemptDetailResponse(CardAttempt attempt) {
        return switch (attempt.getStatus()) {
            case PENDING -> {
                LocalDateTime expiresAt = attempt.getCreatedAt().plus(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()));
                yield AttemptDetailResponse.fromPending(attempt, expiresAt);
            }
            case UPLOADED -> AttemptDetailResponse.fromUploaded(attempt);
            case PROCESSING -> AttemptDetailResponse.fromProcessing(attempt, resolveProcessingStep(attempt));
            case COMPLETED -> {
                // CardFeedback 조회 (1:1 관계)
                CardFeedback feedback = cardFeedbackRepository.findByAttemptId(attempt.getId())
                    .orElse(null);
                yield AttemptDetailResponse.fromCompleted(attempt, feedback);
            }
            case FAILED -> AttemptDetailResponse.fromFailed(attempt);
            case EXPIRED -> AttemptDetailResponse.fromExpired(attempt);
        };
    }

    /**
     * 읽기용 Presigned URL 생성 (AI 서버가 S3에서 파일 다운로드용)
     */
    private String generateReadPresignedUrl(String objectKey) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(attemptProperties.getPresignedUrlExpirationHours()))
                .getObjectRequest(builder -> builder
                    .bucket(s3Properties.getBucket())
                    .key(objectKey)
                )
                .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("읽기용 Presigned URL 생성 실패 - objectKey: {}", objectKey, e);
            throw new BusinessException(ErrorCode.S3_UPLOAD_ERROR);
        }
    }

    private Map<String, Object> resolveCriteria(Card card) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("maxScore", 100);
        defaults.put("keywords", List.of(card.getKeyword().getName()));

        // KnowledgeBaseService 사용 (캐싱 적용)
        List<String> knowledgeContents = knowledgeBaseService.getModelAnswersByKeyword(
            card.getKeyword().getId()
        );
        if (!knowledgeContents.isEmpty()) {
            defaults.put("knowledgeBase", knowledgeContents);
        }
        return defaults;
    }

    private AttemptProcessingStep resolveProcessingStep(CardAttempt attempt) {
        return (attempt.getSttText() == null)
            ? AttemptProcessingStep.AUDIO_ANALYSIS
            : AttemptProcessingStep.FEEDBACK_GENERATION;
    }

    private String mapSttErrorCode(BusinessException e) {
        return switch (e.getErrorCode()) {
            case INVALID_AUDIO_URL -> "STT_RECOGNIZE_FAILED";
            case AI_SERVICE_UNAVAILABLE, S3_UPLOAD_ERROR -> "STT_SERVER_ERROR";
            default -> "UNKNOWN_ERROR";
        };
    }
}
