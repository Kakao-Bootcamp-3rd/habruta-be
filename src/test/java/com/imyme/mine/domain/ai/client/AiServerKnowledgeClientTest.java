package com.imyme.mine.domain.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.ai.dto.knowledge.*;
import com.imyme.mine.global.config.AiServerProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * AiServerClient Knowledge API 테스트
 * - Knowledge Candidate Batch API 테스트
 * - Knowledge Evaluation API 테스트
 */
@ExtendWith(MockitoExtension.class)
class AiServerKnowledgeClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AiServerProperties properties;

    @Mock
    private ObjectMapper objectMapper;

    private AiServerClient aiServerClient;

    @BeforeEach
    void setUp() {
        when(properties.getBaseUrl()).thenReturn("https://test-ai-server.com");
        when(properties.getSecret()).thenReturn("test-secret");

        aiServerClient = new AiServerClient(properties, restTemplate, objectMapper);
    }

    // ==================== Knowledge Candidate Batch API ====================

    @Test
    @DisplayName("지식 후보 배치 생성 성공")
    void createKnowledgeCandidatesBatch_Success() {
        // given
        KnowledgeCandidateBatchRequest request = new KnowledgeCandidateBatchRequest(
            List.of(
                new FeedbackItem("fb_001", "프로세스", "프로세스는 실행 중인 프로그램입니다."),
                new FeedbackItem("fb_002", "스레드", "스레드는 프로세스 내 실행 단위입니다.")
            )
        );

        // Mock 응답 생성 (구조는 그대로 유지)
        KnowledgeCandidateBatchResponse mockResponse = new KnowledgeCandidateBatchResponse(
            true,
            new KnowledgeCandidateBatchResponse.Data(
                2,
                List.of(
                    new KnowledgeCandidate("fb_001", "프로세스", "프로세스는 독립된 메모리 공간에서 실행되는 프로그램의 인스턴스입니다.", List.of(0.1, 0.2, 0.3)),
                    new KnowledgeCandidate("fb_002", "스레드", "스레드는 프로세스 내에서 실행되는 경량 실행 단위입니다.", List.of(0.4, 0.5, 0.6))
                )
            ),
            null
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeCandidateBatchResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // when
        // [변경됨] 반환 타입이 List<KnowledgeCandidate>로 변경됨
        List<KnowledgeCandidate> result = aiServerClient.createKnowledgeCandidatesBatch(request);

        // then
        // [변경됨] 리스트 껍질이 벗겨졌으므로 바로 사이즈 확인
        assertThat(result).hasSize(2);

        // [변경됨] 첫 번째 요소 검증
        assertThat(result.get(0).keyword()).isEqualTo("프로세스");
        assertThat(result.get(0).refinedText()).contains("독립된 메모리 공간");
        assertThat(result.get(0).embedding()).hasSize(3);

        // [변경됨] 두 번째 요소 검증
        assertThat(result.get(1).keyword()).isEqualTo("스레드");
        assertThat(result.get(1).refinedText()).contains("경량 실행 단위");
    }

    @Test
    @DisplayName("지식 후보 배치 생성 실패 - 응답 데이터 null")
    void createKnowledgeCandidatesBatch_NullResponse() {
        // given
        KnowledgeCandidateBatchRequest request = new KnowledgeCandidateBatchRequest(
            List.of(new FeedbackItem("fb_001", "프로세스", "프로세스는 실행 중인 프로그램입니다."))
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeCandidateBatchResponse.class)
        )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // when & then
        assertThatThrownBy(() -> aiServerClient.createKnowledgeCandidatesBatch(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("지식 후보 배치 생성 실패 - 400 Bad Request")
    void createKnowledgeCandidatesBatch_BadRequest() {
        // given
        KnowledgeCandidateBatchRequest request = new KnowledgeCandidateBatchRequest(
            List.of(new FeedbackItem("fb_001", "프로세스", "프로세스는 실행 중인 프로그램입니다."))
        );

        // 400 에러 발생 시뮬레이션
        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeCandidateBatchResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

        // when & then
        // AiServerClient 내부에서 400 -> INVALID_CANDIDATE 로 매핑하는지 확인
        assertThatThrownBy(() -> aiServerClient.createKnowledgeCandidatesBatch(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CANDIDATE);
    }

    @Test
    @DisplayName("지식 후보 배치 생성 실패 - 500 Internal Server Error")
    void createKnowledgeCandidatesBatch_ServerError() {
        // given
        KnowledgeCandidateBatchRequest request = new KnowledgeCandidateBatchRequest(
            List.of(new FeedbackItem("fb_001", "프로세스", "프로세스는 실행 중인 프로그램입니다."))
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeCandidateBatchResponse.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"));

        // when & then
        assertThatThrownBy(() -> aiServerClient.createKnowledgeCandidatesBatch(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("지식 후보 배치 생성 실패 - Timeout")
    void createKnowledgeCandidatesBatch_Timeout() {
        // given
        KnowledgeCandidateBatchRequest request = new KnowledgeCandidateBatchRequest(
            List.of(new FeedbackItem("fb_001", "프로세스", "프로세스는 실행 중인 프로그램입니다."))
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeCandidateBatchResponse.class)
        )).thenThrow(new ResourceAccessException("Connection timeout"));

        // when & then
        assertThatThrownBy(() -> aiServerClient.createKnowledgeCandidatesBatch(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    // ==================== Knowledge Evaluation API ====================

    @Test
    @DisplayName("지식 평가 성공 - UPDATE 결정")
    void evaluateKnowledge_UpdateDecision() {
        // given
        KnowledgeEvaluationRequest request = new KnowledgeEvaluationRequest(
            new EvaluationCandidate("프로세스는 독립된 메모리 공간에서 실행되는 프로그램의 인스턴스입니다.", "fb_001"),
            List.of(
                new SimilarKnowledge("kb_100", "프로세스는 실행 중인 프로그램입니다.", 0.92),
                new SimilarKnowledge("kb_205", "PCB는 프로세스의 상태 정보를 담는 자료구조입니다.", 0.87)
            )
        );

        KnowledgeEvaluationResponse mockResponse = new KnowledgeEvaluationResponse(
            true,
            new KnowledgeEvaluationResponse.Data(
                "UPDATE",
                "kb_100",
                "프로세스는 독립된 메모리 공간에서 실행되는 프로그램의 인스턴스입니다. PCB를 통해 상태 정보를 관리합니다.",
                List.of(0.1, 0.2, 0.3),
                "기존 지식이 너무 간략하므로 후보의 상세 설명을 추가합니다."
            ),
            null
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeEvaluationResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // when
        KnowledgeEvaluationResponse.Data result = aiServerClient.evaluateKnowledge(request);

        // then
        assertThat(result.decision()).isEqualTo("UPDATE");
        assertThat(result.targetId()).isEqualTo("kb_100");
        assertThat(result.finalContent()).contains("PCB");
        assertThat(result.finalVector()).isNotEmpty();
        assertThat(result.reasoning()).contains("상세 설명");
    }

    @Test
    @DisplayName("지식 평가 성공 - IGNORE 결정")
    void evaluateKnowledge_IgnoreDecision() {
        // given
        KnowledgeEvaluationRequest request = new KnowledgeEvaluationRequest(
            new EvaluationCandidate("프로세스는 실행 중인 프로그램입니다.", "fb_001"),
            List.of(new SimilarKnowledge("kb_100", "프로세스는 실행 중인 프로그램입니다.", 0.99))
        );

        KnowledgeEvaluationResponse mockResponse = new KnowledgeEvaluationResponse(
            true,
            new KnowledgeEvaluationResponse.Data(
                "IGNORE",
                null,
                null,
                null,
                "기존 지식과 동일하므로 무시합니다."
            ),
            null
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeEvaluationResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // when
        KnowledgeEvaluationResponse.Data result = aiServerClient.evaluateKnowledge(request);

        // then
        assertThat(result.decision()).isEqualTo("IGNORE");
        assertThat(result.targetId()).isNull();
        assertThat(result.finalContent()).isNull();
        assertThat(result.finalVector()).isNull();
        assertThat(result.reasoning()).contains("무시");
    }

    @Test
    @DisplayName("지식 평가 실패 - 408 Timeout")
    void evaluateKnowledge_Timeout() {
        // given
        KnowledgeEvaluationRequest request = new KnowledgeEvaluationRequest(
            new EvaluationCandidate("프로세스는 독립된 메모리 공간에서 실행되는 프로그램의 인스턴스입니다.", "fb_001"),
            List.of(new SimilarKnowledge("kb_100", "프로세스는 실행 중인 프로그램입니다.", 0.92))
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeEvaluationResponse.class)
        )).thenThrow(new ResourceAccessException("LLM timeout"));

        // when & then
        assertThatThrownBy(() -> aiServerClient.evaluateKnowledge(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LLM_TIMEOUT);
    }

    @Test
    @DisplayName("지식 평가 실패 - 응답 데이터 null")
    void evaluateKnowledge_NullResponse() {
        // given
        KnowledgeEvaluationRequest request = new KnowledgeEvaluationRequest(
            new EvaluationCandidate("프로세스는 독립된 메모리 공간에서 실행되는 프로그램의 인스턴스입니다.", "fb_001"),
            List.of(new SimilarKnowledge("kb_100", "프로세스는 실행 중인 프로그램입니다.", 0.92))
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(KnowledgeEvaluationResponse.class)
        )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // when & then
        assertThatThrownBy(() -> aiServerClient.evaluateKnowledge(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
}
