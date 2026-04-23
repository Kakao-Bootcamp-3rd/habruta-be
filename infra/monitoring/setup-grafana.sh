#!/bin/bash

# Grafana 대시보드 자동 설정 스크립트
# Prometheus 데이터소스 추가 및 대시보드 import

set -e

GRAFANA_URL="http://localhost:3000"
GRAFANA_USER="admin"
GRAFANA_PASS="admin"

echo "📊 Grafana 대시보드 설정 시작"
echo ""

# Grafana 실행 확인
if ! curl -s "$GRAFANA_URL/api/health" > /dev/null 2>&1; then
    echo "❌ Grafana가 실행되지 않았습니다."
    echo "   먼저 ./start-monitoring.sh 를 실행하세요."
    exit 1
fi

echo "✅ Grafana 실행 확인됨"
echo ""

# 1. Prometheus 데이터소스 추가
echo "📡 Prometheus 데이터소스 추가 중..."

DATASOURCE_PAYLOAD='{
  "name": "Prometheus",
  "type": "prometheus",
  "url": "http://prometheus:9090",
  "access": "proxy",
  "isDefault": true,
  "jsonData": {
    "httpMethod": "POST",
    "timeInterval": "15s"
  }
}'

DATASOURCE_RESPONSE=$(curl -s -u "$GRAFANA_USER:$GRAFANA_PASS" \
  -X POST \
  -H "Content-Type: application/json" \
  -d "$DATASOURCE_PAYLOAD" \
  "$GRAFANA_URL/api/datasources")

if echo "$DATASOURCE_RESPONSE" | grep -q "id"; then
    echo "✅ Prometheus 데이터소스 추가 완료"
elif echo "$DATASOURCE_RESPONSE" | grep -q "already exists"; then
    echo "ℹ️  Prometheus 데이터소스가 이미 존재합니다"
else
    echo "⚠️  데이터소스 추가 중 문제 발생:"
    echo "$DATASOURCE_RESPONSE"
fi

echo ""

# 2. 대시보드 Import
echo "📊 대시보드 Import 중..."
echo ""

# 대시보드 ID 목록
DASHBOARD_IDS=(
    "11378:JVM (Micrometer)"
    "4701:Spring Boot 2.x Statistics"
    "6756:Spring Boot Statistics"
)

for DASHBOARD_INFO in "${DASHBOARD_IDS[@]}"; do
    IFS=':' read -r DASHBOARD_ID DASHBOARD_NAME <<< "$DASHBOARD_INFO"

    echo "  → ${DASHBOARD_NAME} (ID: ${DASHBOARD_ID})"

    IMPORT_PAYLOAD="{
      \"dashboard\": {
        \"id\": null
      },
      \"inputs\": [{
        \"name\": \"DS_PROMETHEUS\",
        \"type\": \"datasource\",
        \"pluginId\": \"prometheus\",
        \"value\": \"Prometheus\"
      }],
      \"overwrite\": true,
      \"pluginId\": \"prometheus\",
      \"folderId\": 0,
      \"dashboard\": $(curl -s "https://grafana.com/api/dashboards/${DASHBOARD_ID}/revisions/latest/download")
    }"

    IMPORT_RESPONSE=$(curl -s -u "$GRAFANA_USER:$GRAFANA_PASS" \
      -X POST \
      -H "Content-Type: application/json" \
      -d "$IMPORT_PAYLOAD" \
      "$GRAFANA_URL/api/dashboards/import" 2>&1)

    if echo "$IMPORT_RESPONSE" | grep -q "uid"; then
        echo "    ✅ 완료"
    else
        echo "    ⚠️  이미 존재하거나 문제 발생"
    fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 Grafana 설정 완료!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📊 Grafana: $GRAFANA_URL"
echo "   Username: $GRAFANA_USER"
echo "   Password: $GRAFANA_PASS"
echo ""
echo "💡 대시보드 확인:"
echo "   → Dashboards 메뉴에서 'JVM (Micrometer)' 클릭"
echo ""