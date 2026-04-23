// 스트레스 테스트 - 시스템 한계치 찾기
// 점진적으로 부하를 증가시켜 시스템이 어느 시점에 실패하는지 확인
// 사용법: k6 run stress-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 100 },   // 100명까지
    { duration: '3m', target: 200 },   // 200명까지
    { duration: '2m', target: 300 },   // 300명까지
    { duration: '2m', target: 400 },   // 400명까지 (한계 테스트)
    { duration: '5m', target: 400 },   // 400명 유지
    { duration: '2m', target: 0 },     // 종료
  ],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/categories`);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 1s': (r) => r.timings.duration < 1000,
  });

  sleep(1);
}