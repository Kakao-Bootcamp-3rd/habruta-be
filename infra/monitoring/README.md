# 🔍 MINE 모니터링 가이드

Spring Boot 애플리케이션 모니터링 및 부하 테스트

---

## 📊 구성

- **Prometheus** - 메트릭 수집 (http://localhost:9092)
- **Grafana** - 대시보드 시각화 (http://localhost:3000)
- **K6** - 부하 테스트 도구

---

## 🚀 빠른 시작

### 1. 모니터링 스택 시작

```bash
cd monitoring
./start-monitoring.sh
```

### 2. Spring Boot 앱 시작

```bash
./gradlew bootRun
```

### 3. Grafana 대시보드 설정

1. http://localhost:3000 접속 (admin/admin)
2. **Connections** > **Data sources** > **Add data source** > **Prometheus**
3. URL: `http://prometheus:9090` 입력 → **Save & test**
4. **Dashboards** > **Import** > Dashboard ID **11378** 입력 → **Import**

---

## 🔥 K6 부하 테스트

### K6 설치

```bash
brew install k6
```

### 테스트 실행

```bash
cd monitoring

# 대화형 메뉴
./run-k6-test.sh

# 또는 직접 실행
cd k6-scripts
k6 run basic-load-test.js
```

### 테스트 종류

#### 기본 테스트
| 스크립트 | 소요 시간 | 용도 |
|---------|----------|------|
| `smoke-test.js` | 10초 | 기본 동작 확인 |
| `health-check.js` | 30초 | 헬스체크 테스트 |
| `basic-load-test.js` | 3분 30초 | 정상 트래픽 시뮬레이션 |

#### 시나리오 테스트
| 스크립트 | 소요 시간 | 용도 |
|---------|----------|------|
| `scenario1-basic-endpoints.js` | 2분 | 기본 엔드포인트 |
| `scenario2-card-management.js` | 2분 | 카드 관리 시나리오 |
| `scenario3-learning-flow.js` | 2분 | 학습 흐름 시나리오 |

#### 고급 테스트
| 스크립트 | 소요 시간 | 용도 |
|---------|----------|------|
| `auth-load-test.js` | 2분 | 인증 API 테스트 (e2e 프로파일 필요) |
| `spike-test.js` | 1분 | 급격한 트래픽 증가 |
| `stress-test.js` | 16분 | 시스템 한계치 탐색 |
| `soak-test.js` | 40분 | 장시간 안정성 테스트 |

---

## 📈 모니터링 메트릭

### JVM
- Heap 메모리 사용률
- Garbage Collection 빈도/시간
- Thread 수

### HTTP
- 초당 요청 수 (TPS)
- 평균 응답 시간
- 에러율 (4xx, 5xx)

### 데이터베이스
- HikariCP 커넥션 풀 상태
- Active/Idle 연결 수

---

## 🛑 종료

```bash
cd monitoring

# 정지
./stop-monitoring.sh

# 또는
docker-compose -f docker-compose.monitoring.yml down
```

---

## 📚 추천 Grafana 대시보드

- **11378** - JVM (Micrometer) - 메모리, GC, Thread
- **4701** - Spring Boot 2.x - 상세 메트릭
- **6756** - Spring Boot Statistics

---

## 💡 트러블슈팅

### Prometheus 타겟이 DOWN

```bash
# Spring Boot Actuator 확인
curl http://localhost:8080/actuator/prometheus

# Prometheus 재시작
docker restart imyme-prometheus
```

### Grafana "No data"

1. Grafana > Configuration > Data sources > Prometheus
2. URL 확인: `http://prometheus:9090` (localhost 아님!)
3. **Save & test**

---

## 🎓 참고 자료

- [Prometheus 문서](https://prometheus.io/docs/)
- [Grafana 대시보드](https://grafana.com/grafana/dashboards/)
- [K6 문서](https://k6.io/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)