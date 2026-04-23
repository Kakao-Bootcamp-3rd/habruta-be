package com.imyme.mine.domain.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.challenge.dto.request.CompleteUploadRequest;
import com.imyme.mine.domain.challenge.dto.request.CreateAttemptRequest;
import com.imyme.mine.domain.challenge.dto.response.CreateAttemptResponse;
import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRankingRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeResultRepository;
import com.imyme.mine.domain.storage.service.StorageService;
import com.imyme.mine.domain.challenge.service.ChallengeGateService;
import com.imyme.mine.global.config.ChallengeMqProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ChallengeAttemptService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ChallengeAttemptServiceTest {

    @Mock ChallengeRepository challengeRepository;
    @Mock ChallengeAttemptRepository challengeAttemptRepository;
    @Mock ChallengeRankingRepository challengeRankingRepository;
    @Mock ChallengeResultRepository challengeResultRepository;
    @Mock UserRepository userRepository;
    @Mock StorageService storageService;
    @Mock ObjectMapper objectMapper;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ChallengeMqProperties mqProperties;
    @Mock ChallengeMqProperties.Routing routing;
    @Mock ChallengeGateService challengeGateService;

    @InjectMocks
    ChallengeAttemptService challengeAttemptService;

    @BeforeEach
    void setUp() {
        lenient().when(mqProperties.getRouting()).thenReturn(routing);
        lenient().when(routing.getFeedbackRequest()).thenReturn("challenge.feedback.request");
        // @Transactional 없는 단위 테스트에서 registerSynchronization() 호출을 허용
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // =========================================================================
    // 참여 시작 — contentType 포함 요청으로 presigned URL 발급
    // =========================================================================

    @Test
    @DisplayName("참여 시작 - 신규 생성 시 presigned URL 발급 (HTTP 201)")
    void createAttempt_newAttempt_returnsPresignedUrl() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getStatus()).thenReturn(ChallengeStatus.OPEN);
        User user = mock(User.class);

        when(challengeRepository.findByIdWithKeyword(1L)).thenReturn(Optional.of(challenge));
        when(challengeAttemptRepository.findByChallengeIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(10L)).thenReturn(user);

        // 신규 attempt는 저장 전 ID가 null이므로 any() 사용
        when(storageService.generateChallengePresignedUrl(eq(10L), eq(1L), any(), eq("audio/webm")))
                .thenReturn(new String[]{"challenges/10/1/100_uuid", "https://s3.presigned.url"});

        CreateAttemptRequest request = new CreateAttemptRequest("audio/webm");
        Map.Entry<CreateAttemptResponse, Boolean> result =
                challengeAttemptService.createAttempt(1L, 10L, request);

        assertThat(result.getValue()).isTrue(); // 신규 생성 → HTTP 201
        assertThat(result.getKey().uploadUrl()).isEqualTo("https://s3.presigned.url");
        assertThat(result.getKey().objectKey()).isEqualTo("challenges/10/1/100_uuid");
        verify(storageService).generateChallengePresignedUrl(eq(10L), eq(1L), any(), eq("audio/webm"));
    }

    @Test
    @DisplayName("참여 시작 - PENDING 재사용 시 기존 objectKey로 URL 재발급 (HTTP 200)")
    void createAttempt_pendingReuse_reusesExistingObjectKey() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getStatus()).thenReturn(ChallengeStatus.OPEN);

        ChallengeAttempt existing = mock(ChallengeAttempt.class);
        when(existing.getId()).thenReturn(99L);
        when(existing.getStatus()).thenReturn(ChallengeAttemptStatus.PENDING);
        when(existing.getAudioKey()).thenReturn("challenges/10/1/99_existing_uuid");

        when(challengeRepository.findByIdWithKeyword(1L)).thenReturn(Optional.of(challenge));
        when(challengeAttemptRepository.findByChallengeIdAndUserId(1L, 10L)).thenReturn(Optional.of(existing));
        when(storageService.reissueChallengePresignedUrl("challenges/10/1/99_existing_uuid", "audio/mp4"))
                .thenReturn("https://s3.presigned.url2");

        CreateAttemptRequest request = new CreateAttemptRequest("audio/mp4");
        Map.Entry<CreateAttemptResponse, Boolean> result =
                challengeAttemptService.createAttempt(1L, 10L, request);

        assertThat(result.getValue()).isFalse(); // 재사용 → HTTP 200
        assertThat(result.getKey().objectKey()).isEqualTo("challenges/10/1/99_existing_uuid");
        // 기존 objectKey 재사용 → generateChallengePresignedUrl 호출 안 됨
        verify(storageService, never()).generateChallengePresignedUrl(any(), any(), any(), any());
        verify(storageService).reissueChallengePresignedUrl("challenges/10/1/99_existing_uuid", "audio/mp4");
    }

    // =========================================================================
    // 업로드 완료 — validateChallengeObjectMetadata 호출 검증
    // =========================================================================

    @Test
    @DisplayName("업로드 완료 - validateChallengeObjectMetadata 호출")
    void completeUpload_callsValidateMetadata() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getStatus()).thenReturn(ChallengeStatus.OPEN);

        ChallengeAttempt attempt = mock(ChallengeAttempt.class);
        when(attempt.getStatus()).thenReturn(ChallengeAttemptStatus.PENDING);
        when(attempt.getChallenge()).thenReturn(challenge);
        when(attempt.getAudioKey()).thenReturn("challenges/10/1/100_uuid");

        when(challengeAttemptRepository.findByIdAndChallengeIdAndUserId(100L, 1L, 10L))
                .thenReturn(Optional.of(attempt));

        CompleteUploadRequest request = new CompleteUploadRequest("challenges/10/1/100_uuid", 45);

        challengeAttemptService.completeUpload(1L, 100L, 10L, request);

        verify(storageService).validateChallengeObjectMetadata("challenges/10/1/100_uuid");
    }

    @Test
    @DisplayName("업로드 완료 - objectKey 불일치 시 INVALID_OBJECT_KEY 예외")
    void completeUpload_objectKeyMismatch_throwsException() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getStatus()).thenReturn(ChallengeStatus.OPEN);

        ChallengeAttempt attempt = mock(ChallengeAttempt.class);
        when(attempt.getStatus()).thenReturn(ChallengeAttemptStatus.PENDING);
        when(attempt.getChallenge()).thenReturn(challenge);
        when(attempt.getAudioKey()).thenReturn("challenges/10/1/100_uuid");

        when(challengeAttemptRepository.findByIdAndChallengeIdAndUserId(100L, 1L, 10L))
                .thenReturn(Optional.of(attempt));

        CompleteUploadRequest request = new CompleteUploadRequest("challenges/10/1/other_key", 45);

        assertThatThrownBy(() -> challengeAttemptService.completeUpload(1L, 100L, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_OBJECT_KEY);

        verify(storageService, never()).validateChallengeObjectMetadata(any());
    }
}