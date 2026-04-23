// 인증 포함 부하 테스트 스크립트
// Spring Boot를 e2e 프로파일로 실행해야 함: ./gradlew bootRun --args='--spring.profiles.active=e2e'
// 사용법: k6 run auth-load-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import exec from 'k6/execution';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 20 },  // 30초 동안 20명까지
    { duration: '1m', target: 50 },   // 1분 동안 50명까지
    { duration: '30s', target: 0 },   // 30초 동안 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95%의 요청이 1초 이하
    http_req_failed: ['rate<0.05'],    // 에러율 5% 미만
  },
};

const BASE_URL = 'http://host.docker.internal:8080';

// 로그인하여 JWT 토큰 얻기 (VU별 다른 유저)
function login() {
  const vuId = exec.vu.idInTest;  // VU 번호 (1, 2, 3, ...)
  const deviceUuid = uuidv4();

  const payload = JSON.stringify({
    deviceUuid: deviceUuid,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(`${BASE_URL}/e2e/login/${vuId}`, payload, params);

  if (!check(res, {
    'login status is 200': (r) => r.status === 200,
    'login has accessToken': (r) => JSON.parse(r.body).data.accessToken !== undefined,
  })) {
    console.error('Login failed:', res.status, res.body);
    errorRate.add(1);
    return null;
  }

  const body = JSON.parse(res.body);
  return body.data.accessToken;
}

export default function () {
  // 1. 로그인
  const accessToken = login();

  if (!accessToken) {
    console.error('Failed to get access token');
    return;
  }

  sleep(1);

  // 2. 인증된 요청 헤더
  const authHeaders = {
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
  };

  // 3. 사용자 프로필 조회
  const profileRes = http.get(`${BASE_URL}/users/me`, authHeaders);
  check(profileRes, {
    'profile status is 200': (r) => r.status === 200,
    'profile has user data': (r) => {
      try {
        const data = JSON.parse(r.body);
        return data.data && data.data.nickname !== undefined;
      } catch {
        return false;
      }
    },
  }) || errorRate.add(1);

  sleep(1);

  // 4. 카테고리 조회 (public)
  const categoriesRes = http.get(`${BASE_URL}/categories`);
  check(categoriesRes, {
    'categories status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(1);

  // 5. 키워드 조회 (public)
  const keywordsRes = http.get(`${BASE_URL}/keywords`);
  check(keywordsRes, {
    'keywords status is 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(2);
}