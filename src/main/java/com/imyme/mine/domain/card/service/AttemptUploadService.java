package com.imyme.mine.domain.card.service;

import com.imyme.mine.domain.card.dto.UploadCompleteRequest;
import com.imyme.mine.domain.card.dto.ValidatedAttempt;
import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardRepository;
import com.imyme.mine.global.config.AttemptProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptUploadService {

    private final CardRepository cardRepository;
    private final CardAttemptRepository cardAttemptRepository;
    private final AttemptProperties attemptProperties;

    /**
     * 업로드 완료 처리 1단계: 트랜잭션 내에서 검증 및 상태 업데이트
     * - 외부 HTTP 호출 전에 트랜잭션 종료
     */
    @Transactional
    public ValidatedAttempt markAttemptAsUploaded(Long userId, Long cardId, Long attemptId, UploadCompleteRequest request) {
        Card card = cardRepository.findByIdAndUserIdWithRelations(cardId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        CardAttempt attempt = cardAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        if (!attempt.getCard().getId().equals(card.getId())) {
            throw new BusinessException(ErrorCode.INVALID_CARD_ATTEMPT_MISMATCH);
        }

        if (attempt.getStatus() != AttemptStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        LocalDateTime expiresAt = attempt.getCreatedAt()
            .plus(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()));
        if (LocalDateTime.now().isAfter(expiresAt)) {
            throw new BusinessException(ErrorCode.UPLOAD_EXPIRED);
        }

        if (!request.objectKey().equals(attempt.getAudioKey())) {
            throw new BusinessException(ErrorCode.INVALID_OBJECT_KEY);
        }

        attempt.markUploaded(request.durationSeconds());
        attempt.startProcessing();

        log.info("시도 업로드 상태로 변경 완료 - attemptId: {}, status: {}", attemptId, attempt.getStatus());

        return new ValidatedAttempt(card, attempt);
    }
}
