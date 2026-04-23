// 스모크 테스트 - 기본 동작 확인 (10초)
// 부하 테스트 전에 시스템이 정상적으로 작동하는지 확인
// 사용법: k6 run smoke-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,             // 1명의 가상 사용자
  duration: '10s',    // 10초 동안 실행
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  // 헬스체크
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  check(healthRes, {
    'health check is UP': (r) => r.status === 200 && JSON.parse(r.body).status === 'UP',
  });

  sleep(1);
}