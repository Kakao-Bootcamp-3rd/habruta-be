package com.imyme.mine.domain.challenge.scheduler;

import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.keyword.entity.Keyword;
import com.imyme.mine.domain.keyword.repository.KeywordRepository;
import com.imyme.mine.domain.challenge.service.ChallengeGateService;
import com.imyme.mine.domain.notification.service.NotificationBroadcastService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChallengeScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ChallengeSchedulerTest {

    @Mock ChallengeRepository challengeRepository;
    @Mock ChallengeAttemptRepository challengeAttemptRepository;
    @Mock KeywordRepository keywordRepository;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock NotificationBroadcastService notificationBroadcastService;
    @Mock ChallengeGateService challengeGateService;

    @InjectMocks
    ChallengeScheduler scheduler;

    @BeforeEach
    void setUp() {
        // @Transactional 없는 단위 테스트에서 registerSynchronization() 호출을 허용
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // =========================================================================
    // 00:05 — 내일 챌린지 생성
    // =========================================================================

    @Test
    @DisplayName("내일 챌린지 생성 - 존재하지 않으면 활성 키워드 중 랜덤 선택 후 저장")
    void createTomorrowChallenge_savesChallenge() {
        Keyword keyword = mock(Keyword.class);
        when(keyword.getName()).thenReturn("발표");

        when(challengeRepository.existsByChallengeDate(any())).thenReturn(false);
        when(keywordRepository.findAllWithCategoryByIsActive(true)).thenReturn(List.of(keyword));

        scheduler.createTomorrowChallenge();

        verify(challengeRepository).save(any(Challenge.class));
    }

    @Test
    @DisplayName("내일 챌린지 생성 - 이미 존재하면 저장 건너뜀 (멱등성)")
    void createTomorrowChallenge_skipsIfAlreadyExists() {
        when(challengeRepository.existsByChallengeDate(any())).thenReturn(true);

        scheduler.createTomorrowChallenge();

        verify(challengeRepository, never()).save(any());
        verify(keywordRepository, never()).findAllWithCategoryByIsActive(anyBoolean());
    }

    @Test
    @DisplayName("내일 챌린지 생성 - 활성 키워드 없으면 저장 건너뜀")
    void createTomorrowChallenge_skipsIfNoActiveKeyword() {
        when(challengeRepository.existsByChallengeDate(any())).thenReturn(false);
        when(keywordRepository.findAllWithCategoryByIsActive(true)).thenReturn(List.of());

        scheduler.createTomorrowChallenge();

        verify(challengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("내일 챌린지 생성 - challengeDate가 내일 날짜로 설정됨")
    void createTomorrowChallenge_setsCorrectDate() {
        Keyword keyword = mock(Keyword.class);
        when(keyword.getName()).thenReturn("토론");

        when(challengeRepository.existsByChallengeDate(any())).thenReturn(false);
        when(keywordRepository.findAllWithCategoryByIsActive(true)).thenReturn(List.of(keyword));

        scheduler.createTomorrowChallenge();

        ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
        verify(challengeRepository).save(captor.capture());

        assertThat(captor.getValue().getChallengeDate()).isEqualTo(LocalDate.now().plusDays(1));
    }

    // =========================================================================
    // 22:00 — 챌린지 OPEN
    // =========================================================================

    @Test
    @DisplayName("챌린지 OPEN - 오늘 SCHEDULED 챌린지 존재 시 open() 호출")
    void openChallenge_callsOpenOnChallenge() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getId()).thenReturn(1L);
        when(challenge.getKeywordText()).thenReturn("발표");
        when(challengeRepository.findByChallengeDateAndStatus(LocalDate.now(), ChallengeStatus.SCHEDULED))
                .thenReturn(Optional.of(challenge));

        scheduler.openChallenge();

        verify(challenge).open();
    }

    @Test
    @DisplayName("챌린지 OPEN - 대상 없으면 예외 없이 종료")
    void openChallenge_noTargetNoException() {
        when(challengeRepository.findByChallengeDateAndStatus(any(), any())).thenReturn(Optional.empty());

        scheduler.openChallenge(); // 예외 없이 정상 완료
    }

    // =========================================================================
    // 22:10 — 챌린지 CLOSED
    // =========================================================================

    @Test
    @DisplayName("챌린지 CLOSED - OPEN 챌린지 존재 시 close() 호출")
    void closeChallenge_callsCloseOnChallenge() {
        Challenge challenge = mock(Challenge.class);
        when(challengeRepository.findFirstByStatusOrderByIdDesc(ChallengeStatus.OPEN))
                .thenReturn(Optional.of(challenge));

        scheduler.closeChallenge();

        verify(challenge).close();
    }

    @Test
    @DisplayName("챌린지 CLOSED - 대상 없으면 예외 없이 종료")
    void closeChallenge_noTargetNoException() {
        when(challengeRepository.findFirstByStatusOrderByIdDesc(ChallengeStatus.OPEN)).thenReturn(Optional.empty());

        scheduler.closeChallenge(); // 예외 없이 정상 완료
    }

    // =========================================================================
    // 22:11:30 — 분석 게이트 타임아웃 (CLOSED + 90초)
    // =========================================================================

    @Test
    @DisplayName("분석 게이트 타임아웃 - CLOSED 챌린지 존재 시 closeGate() 호출")
    void startAnalyzing_callsCloseGateWhenClosedChallengeExists() {
        Challenge challenge = mock(Challenge.class);
        when(challenge.getId()).thenReturn(1L);
        when(challengeRepository.findFirstByStatusOrderByIdDesc(ChallengeStatus.CLOSED))
                .thenReturn(Optional.of(challenge));

        scheduler.startAnalyzing();

        verify(challengeGateService).closeGate(1L);
    }

    @Test
    @DisplayName("ANALYZING 전환 - 대상 없으면 예외 없이 종료")
    void startAnalyzing_noTargetNoException() {
        when(challengeRepository.findFirstByStatusOrderByIdDesc(ChallengeStatus.CLOSED)).thenReturn(Optional.empty());

        scheduler.startAnalyzing(); // 예외 없이 정상 완료
    }
}