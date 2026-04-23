# PvP Mode API Request Order (Release)

- Base URL: `https://release.imymemine.kr/server`

## 1) 방 목록 조회 (선택)
- API: `GET /pvp/rooms?status=OPEN&size=20`
- 요청 DTO: 없음 (query param)
- 응답 DTO: `RoomListResponse` (`ApiResponse<RoomListResponse>`)
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "rooms": [
      {
        "room": { "id": 101, "name": "면접 연습방" },
        "category": { "id": 1, "name": "자기소개" },
        "status": "OPEN",
        "host": { "id": 11, "nickname": "호스트", "profileImageUrl": null, "level": 3 },
        "guest": null,
        "createdAt": "2026-03-19T14:30:00"
      }
    ],
    "meta": { "size": 1, "hasNext": false, "nextCursor": null }
  },
  "timestamp": "2026-03-19T05:30:00Z"
}
```

## 2) 호스트 로그인
- API: `POST /e2e/login/host`
- 요청 DTO: `E2ELoginRequest`
- 응답 DTO: `OAuthLoginResponse` (`ApiResponse<OAuthLoginResponse>`)
- 요청 DTO 포맷:
```json
{
  "deviceUuid": "22222222-2222-2222-2222-222222222222"
}
```

## 3) 게스트 로그인
- API: `POST /e2e/login/guest`
- 요청 DTO: `E2ELoginRequest`
- 응답 DTO: `OAuthLoginResponse` (`ApiResponse<OAuthLoginResponse>`)
- 요청 DTO 포맷:
```json
{
  "deviceUuid": "33333333-3333-3333-3333-333333333333"
}
```

## 4) 방 생성 (Host)
- API: `POST /pvp/rooms`
- 요청 DTO: `CreateRoomRequest`
- 응답 DTO: `RoomResponse` (`ApiResponse<RoomResponse>`)
- 요청 DTO 포맷:
```json
{
  "categoryId": 1,
  "roomName": "k6-pvp-room"
}
```
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "room": { "id": 101, "name": "k6-pvp-room" },
    "category": { "id": 1, "name": "자기소개" },
    "status": "OPEN",
    "keyword": null,
    "host": { "id": 11, "nickname": "호스트", "profileImageUrl": null, "level": 3 },
    "guest": null,
    "createdAt": "2026-03-19T14:31:00",
    "matchedAt": null,
    "startedAt": null,
    "thinkingEndsAt": null,
    "message": "방이 생성되었습니다."
  },
  "message": "방이 생성되었습니다.",
  "timestamp": "2026-03-19T05:31:00Z"
}
```

## 5) WebSocket 연결 + 세션 등록 (양쪽)
- API(Handshake): `GET /ws` (STOMP over WebSocket)
- 구독: `/topic/pvp/{roomId}`
- 전송: `/app/pvp/{roomId}/register-session`
- 요청 DTO: 없음 (STOMP 메시지 body 없이 호출 가능)
- 응답 DTO: 없음 (WS 이벤트 수신)

## 6) 방 입장 (Guest)
- API: `POST /pvp/rooms/{roomId}/join`
- 요청 DTO: 없음
- 응답 DTO: `RoomResponse` (`ApiResponse<RoomResponse>`)

## 7) 방 상태 조회
- API: `GET /pvp/rooms/{roomId}`
- 요청 DTO: 없음
- 응답 DTO: `RoomResponse` (`ApiResponse<RoomResponse>`)

## 8) 녹음 시작 준비 (READY)
- API: `POST /pvp/rooms/{roomId}/start-recording`
- 요청 DTO: 없음
- 응답 DTO: `RoomResponse` (`ApiResponse<RoomResponse>`)

## 9) 제출 URL 발급 (양쪽)
- API: `POST /pvp/rooms/{roomId}/submissions`
- 요청 DTO: `CreateSubmissionRequest`
- 응답 DTO: `SubmissionResponse` (`ApiResponse<SubmissionResponse>`)
- 요청 DTO 포맷:
```json
{
  "fileName": "voice.webm",
  "contentType": "audio/webm",
  "fileSize": 102400
}
```
- 응답 DTO 포맷:
```json
{
  "success": true,
  "data": {
    "submissionId": 9001,
    "roomId": 101,
    "uploadUrl": "https://s3-presigned-url...",
    "audioUrl": "pvp/submissions/9001/voice.webm",
    "expiresIn": 300,
    "status": "PENDING",
    "submittedAt": null,
    "message": null
  },
  "message": "녹음 제출 URL이 발급되었습니다.",
  "timestamp": "2026-03-19T05:32:00Z"
}
```

## 10) S3 업로드 (양쪽)
- API: `PUT {uploadUrl}` (Presigned URL)
- 요청 DTO: 없음 (오디오 바이너리 업로드)
- 응답 DTO: 없음 (S3 응답)

## 11) 제출 완료 통지 (양쪽)
- API: `POST /pvp/rooms/submissions/{submissionId}/complete`
- 요청 DTO: `CompleteSubmissionRequest`
- 응답 DTO: `SubmissionResponse` (`ApiResponse<SubmissionResponse>`)
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
    "submissionId": 9001,
    "roomId": 101,
    "uploadUrl": null,
    "audioUrl": null,
    "expiresIn": null,
    "status": "UPLOADED",
    "submittedAt": "2026-03-19T14:33:00",
    "message": "상대방의 제출을 기다리고 있습니다."
  },
  "timestamp": "2026-03-19T05:33:00Z"
}
```

## 12) 결과 조회
- API: `GET /pvp/rooms/{roomId}/result`
- 요청 DTO: 없음
- 응답 DTO: `RoomResultResponse` (`ApiResponse<RoomResultResponse>`)
- 응답 DTO 포맷 (PROCESSING):
```json
{
  "success": true,
  "data": {
    "room": { "id": 101, "name": "k6-pvp-room" },
    "status": "PROCESSING",
    "message": "AI 분석 중입니다."
  },
  "timestamp": "2026-03-19T05:33:10Z"
}
```
- 응답 DTO 포맷 (FINISHED):
```json
{
  "success": true,
  "data": {
    "room": { "id": 101, "name": "k6-pvp-room" },
    "category": { "id": 1, "name": "자기소개" },
    "keyword": { "id": 18, "name": "장단점" },
    "status": "FINISHED",
    "myResult": {
      "historyId": 7001,
      "isHidden": false,
      "user": { "id": 11, "nickname": "호스트", "profileImageUrl": null, "level": 3 },
      "score": 82,
      "audioUrl": "https://...",
      "durationSeconds": 30,
      "sttText": "답변 텍스트",
      "feedback": {
        "summary": "핵심 전달이 좋습니다.",
        "keywords": ["논리", "전달력"],
        "facts": "근거 보완 필요",
        "understanding": "질문 의도 파악 양호",
        "personalizedFeedback": "사례를 한 문장 더 추가해보세요."
      }
    },
    "opponentResult": {
      "user": { "id": 12, "nickname": "게스트", "profileImageUrl": null, "level": 2 },
      "score": 75,
      "audioUrl": "https://...",
      "durationSeconds": 28,
      "sttText": "상대 답변 텍스트",
      "feedback": {
        "summary": "전개는 안정적입니다.",
        "keywords": ["자신감"],
        "facts": "사례가 추상적",
        "understanding": "핵심 의도 일부 누락",
        "personalizedFeedback": "숫자 근거를 보강하세요."
      }
    },
    "winner": { "id": 11, "nickname": "호스트", "profileImageUrl": null, "level": 3 },
    "finishedAt": "2026-03-19T14:33:30"
  },
  "timestamp": "2026-03-19T05:33:30Z"
}
```

## 13) 방 나가기
- API: `DELETE /pvp/rooms/{roomId}`
- 요청 DTO: 없음
- 응답 DTO: 없음 (`204 No Content`)
