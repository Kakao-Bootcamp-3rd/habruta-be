import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,         // 가상 사용자 10명
  duration: '30s', // 30초 동안 실행
};

export default function () {
  const res = http.get('http://host.docker.internal:8080/actuator/health');

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(1);
}