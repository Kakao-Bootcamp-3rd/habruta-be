# Release Main Scenarios

## Base URL
- Release Backend API: `https://release.imymemine.kr/server`

## Scenario 1: Solo Mode (AI Chain 포함)

### Goal
- 레벨업 모드의 전체 파이프라인 부하 검증
- `Attempt 생성 -> S3 업로드 -> upload-complete -> STT/AI -> SSE -> 결과 조회` 경로 확인

### Flow
1. `POST /learning/warmup` (선택)
2. `POST /cards/{cardId}/attempts`
3. `POST /learning/presigned-url`
4. S3 `PUT` 업로드 (`uploadUrl` 사용)
5. `PUT /cards/{cardId}/attempts/{attemptId}/upload-complete`
6. `POST /cards/{cardId}/attempts/{attemptId}/stream-token`
7. `GET /cards/{cardId}/attempts/{attemptId}/stream?token=...` (SSE 구독)
8. `GET /cards/{cardId}/attempts/{attemptId}` 최종 결과 조회

### Expected Status Path
- `PENDING -> UPLOADED -> PROCESSING(AUDIO_ANALYSIS/FEEDBACK_GENERATION) -> COMPLETED|FAILED|EXPIRED`

### KPI
- `upload-complete` 성공률
- `COMPLETED` 도달률
- attempt별 E2E 완료 시간 (createAttempt -> COMPLETED)
- SSE 연결 성공률/종료 이벤트 수신률

### Notes
- `POST /cards/{cardId}/attempts` 응답에는 presigned URL이 없음 (attemptId만 반환)
- 업로드 URL은 반드시 `POST /learning/presigned-url`로 별도 발급
- release/dev/prod는 `solo.mq.enabled=true`이므로 MQ 기반 처리 경로가 기본

---

## Scenario 2: PvP Mode (Match + Submit + Result)

### Goal
- PvP 매칭 및 양측 제출 이후 분석 완료까지 부하 검증
- 방 상태 전이 및 비동기 분석 완료 경로 확인

### Flow
1. `GET /pvp/rooms` (선택)
2. Host: `POST /pvp/rooms`
3. (WS) `/ws` 연결 + `/topic/pvp/{roomId}` 구독 + `/app/pvp/{roomId}/register-session`
4. Guest: `POST /pvp/rooms/{roomId}/join`
5. 상태 전이 확인: `MATCHED -> THINKING`
6. Host/Guest: `POST /pvp/rooms/{roomId}/start-recording` (READY)
7. Host/Guest: `POST /pvp/rooms/{roomId}/submissions`
8. S3 `PUT` 업로드 (각자)
9. Host/Guest: `POST /pvp/rooms/submissions/{submissionId}/complete`
10. STT/피드백 비동기 처리 (MQ)
11. `GET /pvp/rooms/{roomId}/result` (FINISHED까지 폴링 또는 WS 이벤트 확인)

### Expected Status Path
- `OPEN -> MATCHED -> THINKING -> RECORDING -> PROCESSING -> FINISHED`

### KPI
- 매칭 성공률
- 양측 제출 완료율
- `FINISHED` 도달률
- room별 완료 시간 (createRoom -> FINISHED)

### Notes
- WS 구독 토픽은 `/topic/pvp/{roomId}`
- 제출 API는 presigned URL 발급용(`createSubmission`)과 완료 통지용(`completeSubmission`)이 분리됨
- 결과 조회 캐시는 `FINISHED` 상태에서만 유의미

---

## Workload Recommendation
- 혼합 비율(초기): Solo 70%, PvP 25%, 로그인/기타 5%
- 인증은 매 요청 로그인 대신 VU 세션 재사용
- 로그인 부하는 별도 시나리오로 분리

## Pre-check
- `GET /server/health` 200
- E2E 로그인 API 응답 확인
- 테스트 계정/카드/키워드 데이터 존재 확인
- S3 권한 및 presigned URL 동작 확인
