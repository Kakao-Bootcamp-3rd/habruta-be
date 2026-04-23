#!/bin/bash

# MINE 통합 모니터링 스택 종료 스크립트

set -e

echo "🛑 MINE 모니터링 스택 종료 중..."
echo ""

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 사용자 선택: 데이터 보존 여부
echo "모니터링 데이터를 어떻게 처리할까요?"
echo ""
echo "1) 컨테이너만 정지 (데이터 보존, 다음 실행 시 기존 데이터 유지)"
echo "2) 컨테이너 삭제 (데이터 보존, 깔끔한 종료)"
echo "3) 컨테이너 + 볼륨 삭제 (⚠️  모든 데이터 삭제, 완전 초기화)"
echo ""
read -p "선택 (1-3): " choice

case $choice in
    1)
        echo ""
        echo "📦 컨테이너 정지 중..."
        docker-compose -f docker-compose.monitoring.yml stop
        echo "✅ 모든 컨테이너가 정지되었습니다."
        echo "   다시 시작: docker-compose -f docker-compose.monitoring.yml start"
        ;;
    2)
        echo ""
        echo "📦 컨테이너 삭제 중 (볼륨 보존)..."
        docker-compose -f docker-compose.monitoring.yml down
        echo "✅ 컨테이너가 삭제되었습니다."
        echo "   데이터는 보존되었으며, 다음 실행 시 기존 데이터가 로드됩니다."
        echo "   다시 시작: ./start-monitoring.sh"
        ;;
    3)
        echo ""
        echo "⚠️  정말 모든 데이터를 삭제하시겠습니까?"
        echo "   (Pinpoint 트레이스 데이터, Prometheus 메트릭, Grafana 대시보드)"
        read -p "확인 (y/N): " confirm

        if [[ $confirm == "y" || $confirm == "Y" ]]; then
            echo ""
            echo "📦 컨테이너 및 볼륨 삭제 중..."
            docker-compose -f docker-compose.monitoring.yml down -v
            echo "✅ 모든 컨테이너와 데이터가 삭제되었습니다."
            echo "   다시 시작: ./start-monitoring.sh"
        else
            echo "❌ 취소되었습니다."
        fi
        ;;
    *)
        echo "❌ 잘못된 선택입니다. 종료합니다."
        exit 1
        ;;
esac

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "💡 Tip: 다음 명령어로 컨테이너 상태를 확인할 수 있습니다."
echo "   docker ps -a --filter 'name=imyme'"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""