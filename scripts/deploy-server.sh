#!/bin/bash
set -e

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}서버 배포 스크립트${NC}"
echo -e "${GREEN}========================================${NC}"

# 환경 변수 체크
if [ -z "$SERVER_HOST" ]; then
    echo -e "${RED}Error: SERVER_HOST 환경 변수가 설정되지 않았습니다.${NC}"
    echo "예: export SERVER_HOST=your-server.com"
    exit 1
fi

if [ -z "$SERVER_USER" ]; then
    export SERVER_USER="ubuntu"
fi

if [ -z "$SSH_KEY_PATH" ]; then
    export SSH_KEY_PATH="~/.ssh/id_rsa"
fi

if [ -z "$ECR_REGISTRY" ] || [ -z "$ECR_REPOSITORY" ]; then
    echo -e "${RED}Error: ECR_REGISTRY 또는 ECR_REPOSITORY 환경 변수가 설정되지 않았습니다.${NC}"
    exit 1
fi

if [ -z "$IMAGE_TAG" ]; then
    export IMAGE_TAG="latest"
    echo -e "${YELLOW}IMAGE_TAG가 설정되지 않아 기본값 사용: ${IMAGE_TAG}${NC}"
fi

# 배포 정보 출력
echo -e "${YELLOW}Server: ${SERVER_USER}@${SERVER_HOST}${NC}"
echo -e "${YELLOW}Image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}${NC}"
echo ""

# docker-compose.yml과 .env 파일 전송
echo -e "${GREEN}[1/4] 배포 파일 전송 중...${NC}"
scp -i ${SSH_KEY_PATH} \
    docker-compose.yml \
    ${SERVER_USER}@${SERVER_HOST}:/home/${SERVER_USER}/mine/backend/

# 서버에서 배포 실행
echo -e "${GREEN}[2/4] 서버에 배포 중...${NC}"
ssh -i ${SSH_KEY_PATH} ${SERVER_USER}@${SERVER_HOST} 'bash -s' <<EOF
set -e

cd /home/${SERVER_USER}/mine/backend

# ECR 로그인
echo "🔐 ECR 로그인 중..."
aws ecr get-login-password --region ${AWS_REGION:-ap-northeast-2} | \
    docker login --username AWS --password-stdin ${ECR_REGISTRY}

# 환경 변수 설정
export ECR_REGISTRY=${ECR_REGISTRY}
export ECR_REPOSITORY=${ECR_REPOSITORY}
export IMAGE_TAG=${IMAGE_TAG}
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}

# 기존 컨테이너 중지 및 제거
echo "🛑 기존 컨테이너 중지 중..."
docker-compose down || true

# 새 이미지 pull
echo "📥 새 이미지 다운로드 중..."
docker-compose pull

# 컨테이너 시작
echo "🚀 컨테이너 시작 중..."
docker-compose up -d

# 헬스체크 대기
echo "🔍 헬스체크 대기 중..."
for i in {1..30}; do
    if docker exec imyme-backend wget --no-verbose --tries=1 --spider http://localhost:8080/health 2>/dev/null; then
        echo "✅ 헬스체크 성공!"
        break
    fi
    echo "⏳ 헬스체크 대기 중... (\$i/30)"
    sleep 2
done

# 최종 헬스체크
if ! docker exec imyme-backend wget --no-verbose --tries=1 --spider http://localhost:8080/health 2>/dev/null; then
    echo "❌ 헬스체크 실패!"
    echo "📋 컨테이너 로그:"
    docker-compose logs --tail=50
    exit 1
fi

# 사용하지 않는 이미지 정리
echo "🧹 사용하지 않는 이미지 정리 중..."
docker image prune -f

echo "✅ 배포 완료!"
docker-compose ps
EOF

# 외부 헬스체크
echo -e "${GREEN}[3/4] 외부 헬스체크 중...${NC}"
if [ ! -z "$BASE_URL" ]; then
    sleep 5
    if curl -fsS "${BASE_URL}/server/health" >/dev/null; then
        echo -e "${GREEN}✅ 외부 헬스체크 성공!${NC}"
    else
        echo -e "${RED}❌ 외부 헬스체크 실패${NC}"
        exit 1
    fi
fi

# 완료
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ 배포 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
echo ""
