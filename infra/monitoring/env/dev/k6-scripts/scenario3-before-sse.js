import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * SSE 도입 전 측정용 - 폴링 시뮬레이션
 *
 * 카드 생성 → 시도 생성 → 3초마다 GET /attempts/{id} 폴링 (최대 30초)
 * → Grafana에서 /attempts/{id} 요청 수, hikari pool 사용률 관찰
 *
 * 사용법:
 *   k6 run scenario3-before-sse.js \
 *     -e BASE_URL=https://release.imymemine.kr/server \
 *     -e JWT_TOKEN=<your-token>
 */

export const options = {
  stages: [
    { duration: '15s', target: 10 },   // 10명까지 ramp-up
    { duration: '60s', target: 50 },   // 50명 유지 (측정 구간)
    { duration: '15s', target: 0 },    // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.1'],
    'http_req_duration{scenario:polling}': ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN;

const CATEGORY_ID = 1;
const KEYWORD_ID = 18;

const POLL_INTERVAL_S = 3;    // 3초마다 폴링 (기존 프론트 동작)
const MAX_POLL_COUNT = 10;    // 최대 10회 = 30초 (AI 평균 분석 시간)

export default function () {
  if (!JWT_TOKEN) {
    console.error('JWT_TOKEN 환경변수가 필요합니다.');
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${JWT_TOKEN}`,
  };

  // 1. 카드 생성 (매 iteration마다 새 카드 → 시도 횟수 제한 우회)
  const cardRes = http.post(
    `${BASE_URL}/cards`,
    JSON.stringify({
      categoryId: CATEGORY_ID,
      keywordId: KEYWORD_ID,
      title: `k6-${__VU}-${__ITER}`,
    }),
    { headers, tags: { scenario: 'create-card' } }
  );

  const cardCreated = check(cardRes, {
    'card created (201)': (r) => r.status === 201,
  });

  if (!cardCreated) {
    console.warn(`카드 생성 실패: ${cardRes.status} ${cardRes.body}`);
    return;
  }

  const cardId = cardRes.json('data.id');

  // 2. 시도 생성
  const attemptRes = http.post(
    `${BASE_URL}/cards/${cardId}/attempts`,
    JSON.stringify({ durationSeconds: 30 }),
    { headers, tags: { scenario: 'create-attempt' } }
  );

  const attemptCreated = check(attemptRes, {
    'attempt created (201)': (r) => r.status === 201,
  });

  if (!attemptCreated) {
    console.warn(`시도 생성 실패: ${attemptRes.status} ${attemptRes.body}`);
    return;
  }

  const attemptId = attemptRes.json('data.attemptId');

  // 3. 폴링: 3초마다 상태 조회 (AI 분석 완료 전까지 무의미한 DB SELECT)
  for (let i = 0; i < MAX_POLL_COUNT; i++) {
    sleep(POLL_INTERVAL_S);

    const pollRes = http.get(
      `${BASE_URL}/cards/${cardId}/attempts/${attemptId}`,
      { headers, tags: { scenario: 'polling' } }
    );

    check(pollRes, {
      'poll success (200)': (r) => r.status === 200,
    });

    const status = pollRes.json('data.status');
    if (status === 'COMPLETED' || status === 'FAILED' || status === 'EXPIRED') {
      console.log(`[before] attemptId=${attemptId} 완료: ${status} (${i + 1}회 폴링)`);
      break;
    }
  }
}

export function handleSummary(data) {
  return {
    'summary-before-sse.json': JSON.stringify(data),
    stdout: '\n📊 [Before SSE] 폴링 부하 테스트 완료\n',
  };
}