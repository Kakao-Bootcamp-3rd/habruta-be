package com.imyme.mine.domain.storage.service;

import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.storage.dto.PresignedUrlRequest;
import com.imyme.mine.domain.storage.dto.PresignedUrlResponse;
import com.imyme.mine.global.config.AttemptProperties;
import com.imyme.mine.global.config.S3Properties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final CardAttemptRepository cardAttemptRepository;
    private final AttemptProperties attemptProperties;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "audio/mpeg",
        "audio/wav",
        "audio/mp4",
        "audio/m4a",
        "audio/webm"
    );

    private static final Set<String> CHALLENGE_ALLOWED_CONTENT_TYPES = Set.of(
        "audio/webm",
        "audio/mp4",
        "audio/m4a",
        "audio/mpeg",
        "audio/wav"
    );

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final long CHALLENGE_MAX_FILE_SIZE = 10 * 1024 * 1024; // 챌린지 10MB
    private static final int PVP_URL_EXPIRATION_MINUTES = 5; // 5분
    private static final int CHALLENGE_URL_EXPIRATION_MINUTES = 5; // 5분

    @Transactional
    public PresignedUrlResponse generatePresignedUrl(Long userId, PresignedUrlRequest request) {
        log.debug("Presigned URL 생성 시작 - userId: {}, attemptId: {}, contentType: {}",
            userId, request.attemptId(), request.contentType());

        CardAttempt attempt = cardAttemptRepository.findById(request.attemptId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        if (!attempt.getCard().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (attempt.getStatus() != AttemptStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        LocalDateTime expiresAt = attempt.getCreatedAt().plus(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()));
        if (LocalDateTime.now().isAfter(expiresAt)) {
            throw new BusinessException(ErrorCode.UPLOAD_EXPIRED);
        }

        String contentType = normalizeContentType(request.contentType());
        String extension = getExtensionFromContentType(contentType);

        Long cardId = attempt.getCard().getId();
        String objectKey = generateObjectKey(userId, cardId, attempt.getId(), extension);

        attempt.reserveAudioKey(objectKey);

        PresignedPutObjectRequest presignedRequest = generatePresignedPutRequest(objectKey, contentType);

        LocalDateTime presignedExpiresAt = LocalDateTime.now().plus(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()));

        log.info("Presigned URL 생성 완료 - attemptId: {}, objectKey: {}", attempt.getId(), objectKey);

        return PresignedUrlResponse.of(
            attempt.getId(),
            presignedRequest.url().toString(),
            contentType,
            objectKey,
            presignedExpiresAt
        );
    }

    private String generateObjectKey(Long userId, Long cardId, Long attemptId, String fileExtension) {
        String uuid = UUID.randomUUID().toString();
        return String.format("audios/%d/%d/%d_%s.%s", userId, cardId, attemptId, uuid, fileExtension);
    }

    private PresignedPutObjectRequest generatePresignedPutRequest(String objectKey, String contentType) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()))
            .putObjectRequest(builder -> builder
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
            )
            .build();

        return s3Presigner.presignPutObject(presignRequest);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        }

        String normalized = contentType.split(";")[0].trim().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        }

        // alias 처리: audio/m4a는 audio/mp4로 서명
        return "audio/m4a".equals(normalized) ? "audio/mp4" : normalized;
    }

    private String getExtensionFromContentType(String contentType) {
        return switch (contentType) {
            case "audio/mpeg" -> "mp3";
            case "audio/wav" -> "wav";
            case "audio/mp4" -> "m4a";
            case "audio/webm" -> "webm";
            default -> throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        };
    }

    /**
     * S3 오브젝트 단건 삭제 (배치용)
     *
     * <p>S3 DeleteObject는 존재하지 않는 키에 대해서도 200을 반환하므로 별도 존재 확인 불필요.
     *
     * @param objectKey 삭제할 S3 오브젝트 키
     */
    public void deleteObject(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(objectKey)
            .build();

        s3Client.deleteObject(request);
        log.info("S3 오브젝트 삭제 완료 - key: {}", objectKey);
    }

    /**
     * S3 오브젝트 복수 삭제 (배치용, 최대 1000개)
     *
     * <p>S3 DeleteObjects API를 사용하여 한 번의 요청으로 최대 1000개 삭제.
     * 호출자는 1000개 초과 시 청크를 나눠 여러 번 호출해야 한다.
     *
     * @param objectKeys 삭제할 S3 오브젝트 키 목록 (최대 1000개)
     * @return 삭제 실패한 키 목록 (성공 시 빈 리스트)
     */
    public List<String> deleteObjects(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }

        List<ObjectIdentifier> identifiers = objectKeys.stream()
            .map(key -> ObjectIdentifier.builder().key(key).build())
            .toList();

        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
            .bucket(s3Properties.getBucket())
            .delete(Delete.builder().objects(identifiers).build())
            .build();

        var response = s3Client.deleteObjects(request);

        List<String> failed = response.errors().stream()
            .map(e -> e.key())
            .toList();

        if (!failed.isEmpty()) {
            log.warn("S3 오브젝트 삭제 실패 {}건 - keys: {}", failed.size(), failed);
        }
        log.info("S3 오브젝트 일괄 삭제 - 성공: {}건, 실패: {}건",
            response.deleted().size(), failed.size());

        return failed;
    }

    /**
     * PvP 녹음 제출용 Presigned URL 발급
     */
    public PresignedUrlResponse generatePvpPresignedUrl(Long submissionId, String fileName, String contentType, Long fileSize) {
        log.debug("PvP Presigned URL 생성 시작 - submissionId: {}, contentType: {}, fileSize: {}",
            submissionId, contentType, fileSize);

        // 파일 크기 검증
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        // Content-Type 정규화 및 검증
        String normalizedContentType = normalizeContentType(contentType);
        String extension = getExtensionFromContentType(normalizedContentType);

        // ObjectKey 생성 (pvp/{submissionId}_{uuid}.{ext})
        String uuid = UUID.randomUUID().toString();
        String objectKey = String.format("pvp/%d_%s.%s", submissionId, uuid, extension);

        // Presigned PUT 요청 생성 (5분 만료)
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(PVP_URL_EXPIRATION_MINUTES))
            .putObjectRequest(builder -> builder
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(normalizedContentType)
            )
            .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        LocalDateTime presignedExpiresAt = LocalDateTime.now().plus(Duration.ofMinutes(PVP_URL_EXPIRATION_MINUTES));

        log.info("PvP Presigned URL 생성 완료 - submissionId: {}, objectKey: {}", submissionId, objectKey);

        return PresignedUrlResponse.of(
            submissionId,
            presignedRequest.url().toString(),
            normalizedContentType,
            objectKey,
            presignedExpiresAt
        );
    }

    /**
     * 챌린지 녹음 제출용 Presigned PUT URL 발급
     * - contentType 사전 검증 후 presigned PUT에 Content-Type 제약 포함
     * - fileSize는 받지 않음 — upload-complete 시 HeadObject로 검증
     * - audio/m4a alias 정규화 없음: 프론트 PUT 헤더와 일치시키기 위해 입력값 그대로 사용
     * - 클라이언트는 S3 PUT 요청 시 createAttempt에 보낸 것과 동일한 Content-Type 헤더 필수
     *
     * @return [objectKey, uploadUrl] — objectKey는 attempt에 저장, uploadUrl은 클라이언트에 전달
     */
    public String[] generateChallengePresignedUrl(
            Long userId, Long challengeId, Long attemptId, String contentType) {

        String raw = contentType == null ? null : contentType.split(";")[0].trim().toLowerCase();
        if (raw == null || !CHALLENGE_ALLOWED_CONTENT_TYPES.contains(raw)) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        }

        String ext = getChallengeExtensionFromContentType(raw);
        String objectKey = String.format("challenges/%d/%d/%d_%s.%s",
                userId, challengeId, attemptId, UUID.randomUUID(), ext);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(CHALLENGE_URL_EXPIRATION_MINUTES))
                .putObjectRequest(builder -> builder
                        .bucket(s3Properties.getBucket())
                        .key(objectKey)
                        .contentType(raw)
                )
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        log.info("챌린지 Presigned URL 생성 - attemptId={}, objectKey={}", attemptId, objectKey);
        return new String[]{objectKey, uploadUrl};
    }

    /**
     * 챌린지 PENDING 재발급 — 기존 objectKey 재사용
     * - 새 UUID 없이 동일 key로 presigned URL만 재발급 → orphan 파일 방지
     * - contentType 사전 검증 수행 (신규 발급과 동일 정책)
     *
     * @return uploadUrl — 클라이언트에 전달할 새 presigned PUT URL
     */
    public String reissueChallengePresignedUrl(String objectKey, String contentType) {
        String raw = contentType == null ? null : contentType.split(";")[0].trim().toLowerCase();
        if (raw == null || !CHALLENGE_ALLOWED_CONTENT_TYPES.contains(raw)) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        }

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(CHALLENGE_URL_EXPIRATION_MINUTES))
                .putObjectRequest(builder -> builder
                        .bucket(s3Properties.getBucket())
                        .key(objectKey)
                        .contentType(raw)
                )
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        log.info("챌린지 Presigned URL 재발급 - objectKey={}", objectKey);
        return uploadUrl;
    }

    /**
     * 챌린지 upload-complete 시 S3 HeadObject로 실제 파일 메타데이터 검증
     * - 객체 없음 → UPLOAD_NOT_COMPLETED
     * - 허용되지 않은 content-type → 즉시 deleteObject 후 INVALID_CONTENT_TYPE
     * - 10MB 초과 → 즉시 deleteObject 후 FILE_TOO_LARGE
     */
    public void validateChallengeObjectMetadata(String objectKey) {
        software.amazon.awssdk.services.s3.model.HeadObjectResponse head;
        try {
            head = s3Client.headObject(builder -> builder.bucket(s3Properties.getBucket()).key(objectKey));
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.UPLOAD_NOT_COMPLETED);
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                throw new BusinessException(ErrorCode.UPLOAD_NOT_COMPLETED);
            }
            throw e;
        }

        String raw = head.contentType() == null ? null
                : head.contentType().split(";")[0].trim().toLowerCase();
        if (raw == null || !CHALLENGE_ALLOWED_CONTENT_TYPES.contains(raw)) {
            deleteObjectQuietly(objectKey);
            throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        }

        if (head.contentLength() > CHALLENGE_MAX_FILE_SIZE) {
            deleteObjectQuietly(objectKey);
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private String getChallengeExtensionFromContentType(String contentType) {
        return switch (contentType) {
            case "audio/webm" -> "webm";
            case "audio/mp4", "audio/m4a" -> "m4a";
            case "audio/mpeg" -> "mp3";
            case "audio/wav" -> "wav";
            default -> throw new BusinessException(ErrorCode.INVALID_CONTENT_TYPE);
        };
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            deleteObject(objectKey);
        } catch (Exception e) {
            log.warn("[Challenge] 무효 파일 삭제 실패 - objectKey={}, error={}", objectKey, e.getMessage());
        }
    }

    /**
     * AI 서버 접근용 Presigned GET URL 생성
     * - STT 변환을 위해 AI 서버가 S3 파일을 다운로드할 수 있도록 임시 URL 생성
     * - 1시간 유효
     */
    public String generatePresignedGetUrl(String objectKey) {
        log.debug("Presigned GET URL 생성 시작 - objectKey: {}", objectKey);

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1)) // AI 서버 처리 시간 고려하여 1시간
            .getObjectRequest(builder -> builder
                .bucket(s3Properties.getBucket())
                .key(objectKey)
            )
            .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        String url = presignedRequest.url().toString();

        log.info("Presigned GET URL 생성 완료 - objectKey: {}", objectKey);
        return url;
    }
}
