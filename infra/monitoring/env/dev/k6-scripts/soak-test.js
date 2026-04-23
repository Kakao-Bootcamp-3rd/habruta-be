// 소크 테스트 (Soak Test) - 장시간 안정성 테스트
// 일정 부하를 오랜 시간 유지하여 메모리 누수, 리소스 누적 문제 확인
// 사용법: k6 run soak-test.js
// ⚠️ 주의: 이 테스트는 30분 이상 실행됩니다

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '5m', target: 50 },    // Ramp-up: 50명까지
    { duration: '30m', target: 50 },   // 30분 동안 50명 유지 (실제 운영 부하)
    { duration: '5m', target: 0 },     // Ramp-down: 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 장시간에도 응답 시간 유지
    http_req_failed: ['rate<0.01'],    // 에러율 1% 미만
    errors: ['rate<0.05'],             // 커스텀 에러 5% 미만
  },
};

const BASE_URL = 'http://host.docker.internal:8080';

export default function () {
  // 실제 사용자 행동 시뮬레이션
  const endpoints = [
    '/actuator/health',
    '/categories',
    '/keywords',
  ];

  // 랜덤하게 엔드포인트 선택
  const randomEndpoint = endpoints[Math.floor(Math.random() * endpoints.length)];

  const res = http.get(`${BASE_URL}${randomEndpoint}`);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  }) || errorRate.add(1);

  // 사용자 생각 시간 시뮬레이션 (1-5초 랜덤)
  sleep(Math.random() * 4 + 1);
}