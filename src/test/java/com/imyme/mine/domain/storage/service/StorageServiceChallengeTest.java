package com.imyme.mine.domain.storage.service;

import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.global.config.AttemptProperties;
import com.imyme.mine.global.config.S3Properties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("StorageService 챌린지 검증 단위 테스트")
@ExtendWith(MockitoExtension.class)
class StorageServiceChallengeTest {

    @Mock S3Client s3Client;
    @Mock S3Presigner s3Presigner;
    @Mock S3Properties s3Properties;
    @Mock CardAttemptRepository cardAttemptRepository;
    @Mock AttemptProperties attemptProperties;

    @InjectMocks
    StorageService storageService;

    @BeforeEach
    void setUp() {
        lenient().when(s3Properties.getBucket()).thenReturn("test-bucket");
    }

    // =========================================================================
    // generateChallengePresignedUrl — contentType 사전 검증
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {"audio/webm", "audio/mp4", "audio/m4a", "audio/mpeg", "audio/wav"})
    @DisplayName("generateChallengePresignedUrl - 허용 타입 5종 모두 통과")
    void generatePresignedUrl_allowedTypes_noException(String contentType) throws MalformedURLException {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://s3.presigned.url"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        assertThatCode(() -> storageService.generateChallengePresignedUrl(1L, 1L, 1L, contentType))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("generateChallengePresignedUrl - 허용되지 않는 타입(video/mp4) → INVALID_CONTENT_TYPE")
    void generatePresignedUrl_invalidType_throwsException() {
        assertThatThrownBy(() -> storageService.generateChallengePresignedUrl(1L, 1L, 1L, "video/mp4"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CONTENT_TYPE);
    }

    @Test
    @DisplayName("generateChallengePresignedUrl - null contentType → INVALID_CONTENT_TYPE")
    void generatePresignedUrl_nullType_throwsException() {
        assertThatThrownBy(() -> storageService.generateChallengePresignedUrl(1L, 1L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CONTENT_TYPE);
    }

    // =========================================================================
    // reissueChallengePresignedUrl — PENDING 재발급 (기존 objectKey 재사용)
    // =========================================================================

    @Test
    @DisplayName("reissueChallengePresignedUrl - 기존 objectKey로 새 presigned URL 발급")
    void reissuePresignedUrl_existingKey_returnsNewUrl() throws MalformedURLException {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://s3.reissued.url"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        String url = storageService.reissueChallengePresignedUrl("challenges/1/1/99_existing", "audio/webm");

        assertThat(url).isEqualTo("https://s3.reissued.url");
        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("reissueChallengePresignedUrl - 허용되지 않는 타입 → INVALID_CONTENT_TYPE")
    void reissuePresignedUrl_invalidType_throwsException() {
        assertThatThrownBy(() -> storageService.reissueChallengePresignedUrl("challenges/1/1/99_existing", "video/mp4"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CONTENT_TYPE);

        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    // =========================================================================
    // validateChallengeObjectMetadata — 사후 검증 (HeadObject)
    // =========================================================================

    @Test
    @DisplayName("validateChallengeObjectMetadata - 객체 없음 → UPLOAD_NOT_COMPLETED")
    void validate_objectNotFound_throwsUploadNotCompleted() {
        when(s3Client.headObject(any(Consumer.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> storageService.validateChallengeObjectMetadata("challenges/1/1/100_uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UPLOAD_NOT_COMPLETED);
    }

    @Test
    @DisplayName("validateChallengeObjectMetadata - 허용되지 않은 content-type → deleteObject 후 INVALID_CONTENT_TYPE")
    void validate_invalidContentType_deletesObjectAndThrows() {
        HeadObjectResponse head = HeadObjectResponse.builder()
                .contentType("video/mp4")
                .contentLength(1024L)
                .build();
        when(s3Client.headObject(any(Consumer.class))).thenReturn(head);

        assertThatThrownBy(() -> storageService.validateChallengeObjectMetadata("challenges/1/1/100_uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CONTENT_TYPE);

        verify(s3Client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("validateChallengeObjectMetadata - content-type 없음 → deleteObject 후 INVALID_CONTENT_TYPE")
    void validate_nullContentType_deletesObjectAndThrows() {
        HeadObjectResponse head = HeadObjectResponse.builder()
                .contentLength(1024L)
                .build();
        when(s3Client.headObject(any(Consumer.class))).thenReturn(head);

        assertThatThrownBy(() -> storageService.validateChallengeObjectMetadata("challenges/1/1/100_uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CONTENT_TYPE);

        verify(s3Client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("validateChallengeObjectMetadata - 10MB 초과 → deleteObject 후 FILE_TOO_LARGE")
    void validate_fileTooLarge_deletesObjectAndThrows() {
        long overLimit = 10L * 1024 * 1024 + 1;
        HeadObjectResponse head = HeadObjectResponse.builder()
                .contentType("audio/webm")
                .contentLength(overLimit)
                .build();
        when(s3Client.headObject(any(Consumer.class))).thenReturn(head);

        assertThatThrownBy(() -> storageService.validateChallengeObjectMetadata("challenges/1/1/100_uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);

        verify(s3Client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"audio/webm", "audio/mp4", "audio/m4a", "audio/mpeg", "audio/wav"})
    @DisplayName("validateChallengeObjectMetadata - 허용 타입 5종 + 정상 크기 → 예외 없음")
    void validate_allowedTypes_noException(String contentType) {
        HeadObjectResponse head = HeadObjectResponse.builder()
                .contentType(contentType)
                .contentLength(1024L * 1024)
                .build();
        when(s3Client.headObject(any(Consumer.class))).thenReturn(head);

        assertThatCode(() -> storageService.validateChallengeObjectMetadata("challenges/1/1/100_uuid"))
                .doesNotThrowAnyException();
    }
}