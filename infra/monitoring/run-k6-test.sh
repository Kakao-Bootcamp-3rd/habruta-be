#!/bin/bash

# K6 부하 테스트 실행 헬퍼 스크립트

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/k6-scripts"

echo "🔥 K6 부하 테스트 선택"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 기본 테스트"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1) smoke-test.js               - 스모크 테스트 (10초)"
echo "2) health-check.js             - 헬스체크 테스트 (30초)"
echo "3) basic-load-test.js          - 기본 부하 테스트 (3분 30초)"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 시나리오 테스트"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "4) scenario1-basic-endpoints.js - 기본 엔드포인트 (2분)"
echo "5) scenario2-card-management.js - 카드 관리 시나리오 (2분)"
echo "6) scenario3-learning-flow.js   - 학습 흐름 시나리오 (2분)"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 고급 테스트"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "7) auth-load-test.js           - 인증 포함 부하 테스트 (2분, e2e 프로파일 필요)"
echo "8) spike-test.js               - 스파이크 테스트 (1분 10초)"
echo "9) stress-test.js              - 스트레스 테스트 (16분)"
echo "10) soak-test.js               - 소크 테스트 (40분)"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
read -p "선택 (1-10): " choice

case $choice in
    1)
        TEST_FILE="smoke-test.js"
        TEST_NAME="스모크 테스트"
        ;;
    2)
        TEST_FILE="health-check.js"
        TEST_NAME="헬스체크 테스트"
        ;;
    3)
        TEST_FILE="basic-load-test.js"
        TEST_NAME="기본 부하 테스트"
        ;;
    4)
        TEST_FILE="scenario1-basic-endpoints.js"
        TEST_NAME="시나리오 1: 기본 엔드포인트"
        ;;
    5)
        TEST_FILE="scenario2-card-management.js"
        TEST_NAME="시나리오 2: 카드 관리"
        ;;
    6)
        TEST_FILE="scenario3-learning-flow.js"
        TEST_NAME="시나리오 3: 학습 흐름"
        ;;
    7)
        TEST_FILE="auth-load-test.js"
        TEST_NAME="인증 포함 부하 테스트"
        echo ""
        echo "⚠️  이 테스트는 Spring Boot가 e2e 프로파일로 실행되어야 합니다."
        echo "   ./gradlew bootRun --args='--spring.profiles.active=e2e'"
        echo ""
        read -p "계속하시겠습니까? (y/N): " confirm
        if [[ $confirm != "y" && $confirm != "Y" ]]; then
            echo "취소되었습니다."
            exit 0
        fi
        ;;
    8)
        TEST_FILE="spike-test.js"
        TEST_NAME="스파이크 테스트"
        ;;
    9)
        TEST_FILE="stress-test.js"
        TEST_NAME="스트레스 테스트"
        echo ""
        echo "⚠️  이 테스트는 약 16분이 소요됩니다."
        read -p "계속하시겠습니까? (y/N): " confirm
        if [[ $confirm != "y" && $confirm != "Y" ]]; then
            echo "취소되었습니다."
            exit 0
        fi
        ;;
    10)
        TEST_FILE="soak-test.js"
        TEST_NAME="소크 테스트"
        echo ""
        echo "⚠️  이 테스트는 약 40분이 소요됩니다."
        read -p "계속하시겠습니까? (y/N): " confirm
        if [[ $confirm != "y" && $confirm != "Y" ]]; then
            echo "취소되었습니다."
            exit 0
        fi
        ;;
    *)
        echo "❌ 잘못된 선택입니다."
        exit 1
        ;;
esac

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 ${TEST_NAME} 시작"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📊 Grafana 실시간 모니터링: http://localhost:3000"
echo "🔬 Prometheus Targets: http://localhost:9092/targets"
echo ""

# K6가 설치되어 있는지 확인
if ! command -v k6 &> /dev/null; then
    echo "❌ K6가 설치되어 있지 않습니다."
    echo ""
    echo "설치 방법:"
    echo "  brew install k6"
    echo ""
    echo "또는 Docker 사용:"
    echo "  docker run --rm -i --network host -v \"\$(pwd):/scripts\" grafana/k6 run /scripts/${TEST_FILE}"
    exit 1
fi

# Spring Boot 앱이 실행 중인지 확인
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "⚠️  Spring Boot 앱이 실행되지 않은 것 같습니다."
    echo "   http://localhost:8080/actuator/health 에 연결할 수 없습니다."
    echo ""
    read -p "테스트를 계속 진행하시겠습니까? (y/N): " confirm
    if [[ $confirm != "y" && $confirm != "Y" ]]; then
        echo "취소되었습니다."
        exit 0
    fi
fi

# 결과 저장 디렉토리 생성
RESULTS_DIR="$SCRIPT_DIR/k6-results"
mkdir -p "$RESULTS_DIR"

# 타임스탬프
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULT_FILE="$RESULTS_DIR/${TEST_FILE%.js}-${TIMESTAMP}.json"

echo "🔥 테스트 실행 중..."
echo ""

# K6 실행
k6 run "$TEST_FILE" --summary-export="$RESULT_FILE"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ 테스트 완료!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📄 결과 저장됨: $RESULT_FILE"
echo ""
echo "📊 Grafana에서 결과 확인: http://localhost:3000"
echo ""