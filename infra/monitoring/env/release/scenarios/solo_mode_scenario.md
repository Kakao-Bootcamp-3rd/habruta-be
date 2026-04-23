# Solo Mode API Request Order (Release)

- Base URL: `https://release.imymemine.kr/server`

## 1) 로그인
- API: `POST /e2e/login`
- 요청 DTO: `E2ELoginRequest`
- 응답 DTO: `OAuthLoginResponse` (`ApiResponse<OAuthLoginResponse>`)
- 요청 DTO 포맷:
```json
{
  "deviceUuid": "11111111-1111-1111-1111-111111111111"
}
```
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "accessToken": "string",
    "refreshToken": "string",
    "expiresIn": 3600,
    "user": {
      "id": 18,
      "oauthId": "e2e_test_user",
      "oauthProvider": "E2E_TEST",
      "nickname": "E2E테스터",
      "profileImageUrl": null,
      "level": 1,
      "totalCardCount": 1,
      "activeCardCount": 0,
      "consecutiveDays": 1,
      "winCount": 0,
      "isNewUser": false
    }
  },
  "message": "E2E 테스트 로그인 성공",
  "timestamp": "2026-03-19T05:11:20.529336527Z"
}
```

## 2) (선택) 워밍업
- API: `POST /learning/warmup`
- 요청 DTO: 없음
- 응답 DTO: `WarmupResponse` (`ApiResponse<WarmupResponse>`)
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "message": "STT 서버 워밍업이 시작되었습니다.",
    "status": "WARMING_UP"
  },
  "timestamp": "2026-03-19T05:11:20.529336527Z"
}
```

## 3) 카드 준비(없을 때만)
- API: `POST /cards`
- 요청 DTO: `CardCreateRequest`
- 응답 DTO: `CardResponse` (`ApiResponse<CardResponse>`)
- 요청 DTO 포맷:
```json
{
  "categoryId": 1,
  "keywordId": 18,
  "title": "k6-solo-card"
}
```
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "id": 123,
    "categoryId": 1,
    "categoryName": "자기소개",
    "keywordId": 18,
    "keywordName": "장단점",
    "title": "k6-solo-card",
    "bestLevel": 1,
    "attemptCount": 0,
    "createdAt": "2026-03-19T14:20:00",
    "updatedAt": "2026-03-19T14:20:00"
  },
  "message": "카드가 생성되었습니다.",
  "timestamp": "2026-03-19T05:20:00Z"
}
```

## 4) 시도 생성
- API: `POST /cards/{cardId}/attempts`
- 요청 DTO: `AttemptCreateRequest` (nullable)
- 응답 DTO: `AttemptCreateResponse` (`ApiResponse<AttemptCreateResponse>`)
- 요청 DTO 포맷:
```json
{
  "durationSeconds": 30
}
```
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "cardId": 123,
    "attemptId": 456,
    "attemptNo": 1,
    "status": "PENDING",
    "createdAt": "2026-03-19T14:21:00",
    "expiresAt": "2026-03-19T14:31:00",
    "message": "시도가 생성되었습니다. 10분 내에 업로드를 완료해주세요."
  },
  "timestamp": "2026-03-19T05:21:00Z"
}
```

## 5) Presigned URL 발급
- API: `POST /learning/presigned-url`
- 요청 DTO: `PresignedUrlRequest`
- 응답 DTO: `PresignedUrlResponse` (`ApiResponse<PresignedUrlResponse>`)
- 요청 DTO 포맷:
```json
{
  "attemptId": 456,
  "contentType": "audio/webm"
}
```
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "attemptId": 456,
    "uploadUrl": "https://s3-presigned-url...",
    "contentType": "audio/webm",
    "objectKey": "solo/attempts/456/audio.webm",
    "expiresAt": "2026-03-19T14:26:00"
  },
  "timestamp": "2026-03-19T05:22:00Z"
}
```

## 6) S3 업로드
- API: `PUT {uploadUrl}` (Presigned URL)
- 요청 DTO: 없음 (오디오 바이너리 업로드)
- 응답 DTO: 없음 (S3 응답)

## 7) 업로드 완료 통지
- API: `PUT /cards/{cardId}/attempts/{attemptId}/upload-complete`
- 요청 DTO: `UploadCompleteRequest`
- 응답 DTO: `UploadCompleteResponse` (`ApiResponse<UploadCompleteResponse>`)
- 요청 DTO 포맷:
```json
{
  "objectKey": "solo/attempts/456/audio.webm",
  "durationSeconds": 30
}
```
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "attemptId": 456,
    "status": "PROCESSING",
    "durationSeconds": 30,
    "message": "업로드가 완료되었습니다. AI 분석 대기 중입니다."
  },
  "timestamp": "2026-03-19T05:23:00Z"
}
```

## 8) SSE 토큰 발급
- API: `POST /cards/{cardId}/attempts/{attemptId}/stream-token`
- 요청 DTO: 없음
- 응답 DTO: `StreamTokenResponse` (`ApiResponse<StreamTokenResponse>`)
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  },
  "timestamp": "2026-03-19T05:23:02Z"
}
```

## 9) SSE 구독
- API: `GET /cards/{cardId}/attempts/{attemptId}/stream?token=...`
- 요청 DTO: 없음 (query param: `token`)
- 응답 DTO: `text/event-stream` (`status-update` 이벤트)
- SSE 이벤트 포맷:
```text
event: status-update
data: {"status":"PROCESSING","step":"AUDIO_ANALYSIS"}

event: status-update
data: {"status":"PROCESSING","step":"FEEDBACK_GENERATION"}

event: status-update
data: {"status":"COMPLETED"}
```

## 10) 최종 결과 조회
- API: `GET /cards/{cardId}/attempts/{attemptId}`
- 요청 DTO: 없음
- 응답 DTO: `AttemptDetailResponse` (`ApiResponse<AttemptDetailResponse>`)
- 응답 DTO 포맷(예: COMPLETED):
```json
{
  "success": true,
  "data": {
    "attemptId": 456,
    "attemptNo": 1,
    "cardId": 123,
    "status": "COMPLETED",
    "durationSeconds": 30,
    "sttText": "자기소개 내용...",
    "feedback": {
      "overallScore": 82,
      "level": 3,
      "summary": "핵심이 명확합니다.",
      "keywords": "자기소개,강점",
      "facts": "구체 사례 보완 필요",
      "understanding": "질문 의도 파악 양호",
      "socraticFeedback": "다음 답변에서 근거를 더 추가해보세요."
    },
    "createdAt": "2026-03-19T14:21:00",
    "uploadedAt": "2026-03-19T14:23:00",
    "finishedAt": "2026-03-19T14:23:20"
  },
  "timestamp": "2026-03-19T05:23:20Z"
}
```
