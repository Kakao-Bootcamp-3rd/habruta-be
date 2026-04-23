#!/bin/bash

# MINE 모니터링 스택 시작 스크립트
# Prometheus + Grafana

set -e

echo "🚀 MINE 모니터링 스택 시작 중..."
echo ""

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Docker Compose로 모니터링 스택 시작
echo "📦 Docker Compose 실행 중..."
docker-compose -f docker-compose.monitoring.yml up -d

echo ""
echo "⏳ 컨테이너 준비 중... (5초)"
sleep 5

echo ""
echo "🎉 모니터링 스택이 시작되었습니다!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 접속 정보"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📈 Grafana"
echo "   URL      : http://localhost:3000"
echo "   Username : admin"
echo "   Password : admin"
echo ""
echo "🔬 Prometheus"
echo "   URL      : http://localhost:9092"
echo "   Targets  : http://localhost:9092/targets"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📝 다음 단계:"
echo "   1. Spring Boot 앱 시작: ./gradlew bootRun"
echo "   2. Prometheus에서 타겟 확인 (UP 상태)"
echo "   3. Grafana에서 Prometheus 데이터소스 추가"
echo "   4. Grafana에서 대시보드 import (ID: 11378)"
echo ""
echo "📚 자세한 사용법: monitoring/QUICKSTART.md"
echo ""
echo "🛑 종료하려면: ./stop-monitoring.sh"
echo ""