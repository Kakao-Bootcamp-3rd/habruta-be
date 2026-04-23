import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 시나리오 3: 학습 플로우 부하 테스트
 * - 실제 사용자의 학습 시나리오 시뮬레이션
 * - 카드 조회 → 학습 시도 생성 → 시도 상세 조회
 */

export const options = {
  stages: [
    { duration: '15s', target: 5 },   // 15초 동안 5명까지 증가
    { duration: '30s', target: 15 },  // 30초 동안 15명 유지
    { duration: '15s', target: 0 },   // 15초 동안 0명으로 감소
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95%의 요청이 1초 이하
    http_req_failed: ['rate<0.1'],     // 실패율 10% 이하
    'http_req_duration{scenario:learning}': ['p(95)<800'], // 학습 플로우는 800ms 이하
  },
};

const BASE_URL = 'http://localhost:8080';

// JWT 토큰 (2026-02-13 발급 - 1시간 유효)
const JWT_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2IiwiaWF0IjoxNzcwOTQzNzAyLCJleHAiOjE3NzA5NDczMDJ9.v2yJiQpoLLtCN5KgeSNGIhLD-JowvPxYoDc9e7Sp3Y4';

// 테스트용 카드 ID (user_id=6의 첫번째 카드)
const TEST_CARD_ID = 1;

export default function () {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${JWT_TOKEN}`,
  };

  // 1. 학습 카드 목록 조회
  let res = http.get(`${BASE_URL}/cards`, {
    headers,
    tags: { scenario: 'learning' }
  });
  check(res, {
    'cards loaded': (r) => r.status === 200,
  });

  sleep(1);

  // 2. 특정 카드 상세 조회
  res = http.get(`${BASE_URL}/cards/${TEST_CARD_ID}`, {
    headers,
    tags: { scenario: 'learning' }
  });
  check(res, {
    'card detail loaded': (r) => r.status === 200,
  });

  sleep(2);

  // 3. 학습 시도 생성 (Presigned URL 발급)
  const attemptPayload = JSON.stringify({
    fileName: `test-audio-${Date.now()}.webm`
  });

  res = http.post(
    `${BASE_URL}/cards/${TEST_CARD_ID}/attempts`,
    attemptPayload,
    {
      headers,
      tags: { scenario: 'learning' }
    }
  );

  const attemptCreated = check(res, {
    'attempt created': (r) => r.status === 201,
    'presigned url exists': (r) => r.json('data.presignedUrl') !== undefined,
  });

  if (attemptCreated) {
    const attemptId = res.json('data.attemptId');

    sleep(2);

    // 4. 생성된 시도 상세 조회
    res = http.get(
      `${BASE_URL}/cards/${TEST_CARD_ID}/attempts/${attemptId}`,
      {
        headers,
        tags: { scenario: 'learning' }
      }
    );
    check(res, {
      'attempt detail loaded': (r) => r.status === 200,
      'attempt status is PENDING': (r) => r.json('data.status') === 'PENDING',
    });
  }

  sleep(3);
}

export function handleSummary(data) {
  return {
    'summary-scenario3.json': JSON.stringify(data),
    stdout: '\n✅ 시나리오 3 완료: 학습 플로우 테스트\n',
  };
}