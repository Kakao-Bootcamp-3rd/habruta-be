// 기본 부하 테스트 스크립트
// 사용법: k6 run basic-load-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// 커스텀 메트릭: 에러율
const errorRate = new Rate('errors');

// 부하 테스트 시나리오 설정
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // 30초 동안 10명까지 증가
    { duration: '1m', target: 50 },   // 1분 동안 50명까지 증가
    { duration: '30s', target: 100 }, // 30초 동안 100명까지 증가
    { duration: '1m', target: 100 },  // 1분 동안 100명 유지
    { duration: '30s', target: 0 },   // 30초 동안 0명까지 감소 (Ramp-down)
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95%의 요청이 500ms 이하
    http_req_failed: ['rate<0.01'],   // 에러율 1% 미만
    errors: ['rate<0.1'],             // 커스텀 에러율 10% 미만
  },
};

const BASE_URL = 'http://host.docker.internal:8080';

export default function () {
  // 1. 헬스체크
  let healthRes = http.get(`${BASE_URL}/actuator/health`);
  check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
    'health check is UP': (r) => JSON.parse(r.body).status === 'UP',
  }) || errorRate.add(1);

  sleep(1);

  // 2. 카테고리 조회
  let categoriesRes = http.get(`${BASE_URL}/categories`);
  check(categoriesRes, {
    'categories status is 200': (r) => r.status === 200,
    'categories response time < 200ms': (r) => r.timings.duration < 200,
  }) || errorRate.add(1);

  sleep(1);

  // 3. 키워드 조회
  let keywordsRes = http.get(`${BASE_URL}/keywords`);
  check(keywordsRes, {
    'keywords status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(1);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';
  const enableColors = options.enableColors || false;

  let summary = '\n';
  summary += `${indent}✓ checks.........................: ${(data.metrics.checks.passes / data.metrics.checks.values * 100).toFixed(2)}% ✓ ${data.metrics.checks.passes} ✗ ${data.metrics.checks.fails}\n`;
  summary += `${indent}  data_received..................: ${formatBytes(data.metrics.data_received.values.count)}\n`;
  summary += `${indent}  data_sent......................: ${formatBytes(data.metrics.data_sent.values.count)}\n`;
  summary += `${indent}  http_req_blocked...............: avg=${data.metrics.http_req_blocked.values.avg.toFixed(2)}ms min=${data.metrics.http_req_blocked.values.min.toFixed(2)}ms\n`;
  summary += `${indent}  http_req_duration..............: avg=${data.metrics.http_req_duration.values.avg.toFixed(2)}ms min=${data.metrics.http_req_duration.values.min.toFixed(2)}ms\n`;
  summary += `${indent}    { expected_response:true }...: avg=${data.metrics.http_req_duration.values.avg.toFixed(2)}ms\n`;
  summary += `${indent}  http_req_failed................: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}% ✓ ${data.metrics.http_req_failed.values.passes} ✗ ${data.metrics.http_req_failed.values.fails}\n`;
  summary += `${indent}  http_reqs......................: ${data.metrics.http_reqs.values.count} ${(data.metrics.http_reqs.values.rate).toFixed(2)}/s\n`;
  summary += `${indent}  iteration_duration.............: avg=${data.metrics.iteration_duration.values.avg.toFixed(2)}ms\n`;
  summary += `${indent}  iterations.....................: ${data.metrics.iterations.values.count}\n`;
  summary += `${indent}  vus............................: ${data.metrics.vus.values.min} min=${data.metrics.vus.values.min} max=${data.metrics.vus.values.max}\n`;

  return summary;
}

function formatBytes(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}