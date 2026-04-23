import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 시나리오 1: 기본 엔드포인트 부하 테스트
 * - 인증이 필요 없는 기본 API 테스트
 * - Health check, Prometheus 메트릭 등
 */

export const options = {
  stages: [
    { duration: '30s', target: 20 },  // 30초 동안 20명까지 증가
    { duration: '1m', target: 50 },   // 1분 동안 50명 유지
    { duration: '30s', target: 0 },   // 30초 동안 0명으로 감소
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],  // 95%의 요청이 200ms 이하
    http_req_failed: ['rate<0.01'],    // 실패율 1% 이하
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  // Health Check
  let res = http.get(`${BASE_URL}/actuator/health`);
  check(res, {
    'health status is 200': (r) => r.status === 200,
    'health is UP': (r) => r.json('status') === 'UP',
  });

  sleep(0.5);

  // Master Data - Categories (캐싱 측정용)
  res = http.get(`${BASE_URL}/categories`);
  check(res, {
    'categories status is 200': (r) => r.status === 200,
    'categories is array': (r) => Array.isArray(r.json('data')),
  });

  sleep(0.5);

  // Master Data - Keywords (캐싱 측정용)
  res = http.get(`${BASE_URL}/keywords?categoryId=1`);
  check(res, {
    'keywords status is 200': (r) => r.status === 200,
    'keywords is array': (r) => Array.isArray(r.json('data')),
  });

  sleep(0.5);

  // Prometheus Metrics
  res = http.get(`${BASE_URL}/actuator/prometheus`);
  check(res, {
    'prometheus status is 200': (r) => r.status === 200,
  });

  sleep(0.5);
}

export function handleSummary(data) {
  return {
    'summary-scenario1.json': JSON.stringify(data),
    stdout: '\n✅ 시나리오 1 완료: 기본 엔드포인트 테스트\n',
  };
}