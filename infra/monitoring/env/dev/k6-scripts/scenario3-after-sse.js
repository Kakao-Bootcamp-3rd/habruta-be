import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * SSE 도입 후 측정용 - SSE 구독 시뮬레이션
 *
 * 카드 생성 → 시도 생성 → 스트림 토큰 발급 → SSE 연결
 * → GET /attempts/{id} 폴링이 사라지고 SSE 연결 1개로 대체됨
 * → Grafana에서 /attempts/{id} 요청 수 급감 + thread/heap 변화 관찰
 *
 * 사용법:
 *   k6 run scenario3-after-sse.js \
 *     -e BASE_URL=https://release.imymemine.kr/server \
 *     -e JWT_TOKEN=<your-token>
 *
 * 참고:
 *   실제 오디오 업로드 없이 시도만 생성하므로 SSE는 COMPLETED가 아닌
 *   타임아웃(90초) 또는 EXPIRED로 종료됩니다.
 *   비교에 필요한 핵심 지표(GET /attempts/{id} 요청 수 감소)는 정상 측정됩니다.
 */

export const options = {
  scenarios: {
    default: {
      executor: 'ramping-vus',
      stages: [
        { duration: '15s', target: 10 },   // 10명까지 ramp-up
        { duration: '60s', target: 50 },   // 50명 유지 (측정 구간)
        { duration: '15s', target: 0 },    // ramp-down
      ],
      gracefulRampDown: '95s',             // ramp-down 중 VU 강제 종료 유예
      gracefulStop: '95s',                 // 스테이지 종료 후 VU 강제 종료 유예
    },
  },
  thresholds: {
    // SSE 연결(sse-stream)은 장기 연결이므로 duration threshold 제외
    // 카드 생성 / 시도 생성 / 토큰 발급만 응답시간 측정
    'http_req_duration{scenario:create-card}': ['p(95)<2000'],
    'http_req_duration{scenario:create-attempt}': ['p(95)<2000'],
    'http_req_duration{scenario:stream-token}': ['p(95)<300'],
    // SSE timeout은 정상 종료이므로 실패율 제외
    'http_req_failed{scenario:create-card}': ['rate<0.05'],
    'http_req_failed{scenario:create-attempt}': ['rate<0.05'],
    'http_req_failed{scenario:stream-token}': ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN;

const CATEGORY_ID = 1;
const KEYWORD_ID = 18;

const SSE_TIMEOUT = '90s';

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

  sleep(0.5);

  // 3. SSE 스트림 토큰 발급 (JWT 인증 → 1회용 토큰)
  const tokenRes = http.post(
    `${BASE_URL}/cards/${cardId}/attempts/${attemptId}/stream-token`,
    null,
    { headers, tags: { scenario: 'stream-token' } }
  );

  const tokenOk = check(tokenRes, {
    'stream token issued (200)': (r) => r.status === 200,
    'token exists': (r) => r.json('data.token') !== null,
  });

  if (!tokenOk) {
    console.warn(`스트림 토큰 발급 실패: ${tokenRes.status} ${tokenRes.body}`);
    return;
  }

  const streamToken = tokenRes.json('data.token');

  // 4. SSE 구독 (서버가 COMPLETED/FAILED/EXPIRED 이벤트 보내면 연결 종료)
  //    k6는 SSE를 long-lived GET으로 처리: 연결이 닫힐 때 body를 받음
  const sseRes = http.get(
    `${BASE_URL}/cards/${cardId}/attempts/${attemptId}/stream?token=${streamToken}`,
    {
      headers: { Accept: 'text/event-stream' },
      timeout: SSE_TIMEOUT,
      tags: { scenario: 'sse-stream' },
    }
  );

  check(sseRes, {
    'sse connected (200)': (r) => r.status === 200,
    'sse received terminal event': (r) =>
      r.body.includes('COMPLETED') ||
      r.body.includes('FAILED') ||
      r.body.includes('EXPIRED'),
  });

  console.log(`[after] attemptId=${attemptId} SSE 종료. body 일부: ${sseRes.body.substring(0, 100)}`);
}

export function handleSummary(data) {
  return {
    'summary-after-sse.json': JSON.stringify(data),
    stdout: '\n📊 [After SSE] SSE 부하 테스트 완료\n',
  };
}