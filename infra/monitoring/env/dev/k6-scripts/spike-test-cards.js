// 스파이크 테스트 - N+1 쿼리 포함 (CARDS)
// /cards 엔드포인트로 진짜 문제 확인

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '10s', target: 10 },    // 정상 부하
    { duration: '10s', target: 500 },   // 급격한 증가 (500명)
    { duration: '30s', target: 500 },   // 유지
    { duration: '10s', target: 10 },    // 복구
    { duration: '10s', target: 0 },     // 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],  // N+1 쿼리 고려 5초
    http_req_failed: ['rate<0.3'],      // 에러율 30% 미만
  },
};

const BASE_URL = 'http://localhost:8080';
const JWT_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2IiwiaWF0IjoxNzcwOTcxODQ0LCJleHAiOjE3NzA5NzU0NDR9.Pnjoz9T11dCl_-M5DpYk0qQow4_2OzkGqG4FhQSgJnA';

export default function () {
  const headers = {
    'Authorization': `Bearer ${JWT_TOKEN}`,
  };

  // N+1 쿼리 발생하는 엔드포인트!
  const cardsRes = http.get(`${BASE_URL}/cards`, { headers });
  
  check(cardsRes, {
    'cards status is 200': (r) => r.status === 200,
    'cards response time < 5s': (r) => r.timings.duration < 5000,
  }) || errorRate.add(1);

  sleep(1);
}
