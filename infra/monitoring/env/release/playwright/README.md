# Release Playwright UI Flow

FE 코드(`fe/4-team-IMYME-fe`)의 화면 구조를 기준으로 만든 release 점검 스크립트입니다.

## What It Does

1. `/api/e2e/login`으로 E2E 로그인
2. `/main` 진입 및 핵심 UI 확인
3. `PVP 모드` 버튼 클릭 후 `/pvp` 화면 확인
4. `/main` 복귀 후 `레벨업 모드` 진입
5. 카테고리/키워드 첫 항목 선택
6. 카드 이름 입력 후 생성
7. `/levelup/record` 진입 확인
8. `presigned-url -> S3 upload -> upload-complete` API 호출
9. `/levelup/feedback` 진입 후 attempt 최종 상태(COMPLETED/FAILED/EXPIRED)까지 대기

## Run

`playwright` 패키지가 설치된 위치(예: `fe/4-team-IMYME-fe`)에서 실행하세요.

```bash
cd /Users/wonhyeonseob/Desktop/git/MINE/fe/4-team-IMYME-fe
node /Users/wonhyeonseob/Desktop/git/MINE/be/4-team-IMYME-be/monitoring/env/release/playwright/release_ui_flow.mjs
```

설치가 안 되어 있으면 임시 실행도 가능합니다.

```bash
npx -y -p playwright node /Users/wonhyeonseob/Desktop/git/MINE/be/4-team-IMYME-be/monitoring/env/release/playwright/release_ui_flow.mjs
```

## Env

- `BASE_URL` (default: `https://release.imymemine.kr`)
- `E2E_LOGIN_PATH` (default: `/api/e2e/login`)
- `DEVICE_UUID` (default: random UUID)
- `HEADLESS` (default: `true`, `false`면 브라우저 표시)
- `SLOW_MO` (default: `0`)
- `DEFAULT_TIMEOUT_MS` (default: `15000`)
- `FEEDBACK_TIMEOUT_MS` (default: `180000`)
- `POLL_INTERVAL_MS` (default: `2000`)
- `AUDIO_FILE` (optional: 업로드할 실제 오디오 파일 경로)

예시:

```bash
BASE_URL=https://release.imymemine.kr HEADLESS=false SLOW_MO=250 \
node /Users/wonhyeonseob/Desktop/git/MINE/be/4-team-IMYME-be/monitoring/env/release/playwright/release_ui_flow.mjs
```

실제 오디오 파일 사용 예시:

```bash
BASE_URL=https://release.imymemine.kr \
AUDIO_FILE=/absolute/path/sample.webm \
node /Users/wonhyeonseob/Desktop/git/MINE/be/4-team-IMYME-be/monitoring/env/release/playwright/release_ui_flow.mjs
```
