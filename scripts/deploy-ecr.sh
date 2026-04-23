#!/bin/bash
set -e

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}ECR Docker Image Build & Push${NC}"
echo -e "${GREEN}========================================${NC}"

# 환경 변수 체크
if [ -z "$AWS_REGION" ]; then
    export AWS_REGION="ap-northeast-2"
fi

if [ -z "$ECR_REGISTRY" ] || [ -z "$ECR_REPOSITORY" ]; then
    echo -e "${RED}Error: ECR_REGISTRY 또는 ECR_REPOSITORY 환경 변수가 설정되지 않았습니다.${NC}"
    echo "예: export ECR_REGISTRY=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com"
    echo "예: export ECR_REPOSITORY=imyme-backend"
    exit 1
fi

# 버전 태그 설정
# VERSION 환경 변수가 설정되어 있으면 사용, 없으면 브랜치-커밋-타임스탬프 형식 사용
if [ -z "$VERSION" ]; then
    BRANCH=$(git rev-parse --abbrev-ref HEAD)
    COMMIT_HASH=$(git rev-parse --short HEAD)
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    IMAGE_TAG="${BRANCH}-${COMMIT_HASH}-${TIMESTAMP}"
else
    IMAGE_TAG="${VERSION}"
fi

IMAGE_URI="${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
LATEST_TAG="${ECR_REGISTRY}/${ECR_REPOSITORY}:latest"

echo -e "${YELLOW}Image Tag: ${IMAGE_TAG}${NC}"
echo ""

# ECR 로그인
echo -e "${GREEN}[1/4] ECR 로그인 중...${NC}"
aws ecr get-login-password --region ${AWS_REGION} | \
    docker login --username AWS --password-stdin ${ECR_REGISTRY}

# Docker 이미지 빌드
echo -e "${GREEN}[2/4] Docker 이미지 빌드 중...${NC}"
docker build -t ${IMAGE_URI} .
docker tag ${IMAGE_URI} ${LATEST_TAG}

# ECR에 푸시
echo -e "${GREEN}[3/4] ECR에 이미지 푸시 중...${NC}"
docker push ${IMAGE_URI}
docker push ${LATEST_TAG}

# 결과 출력
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ 이미지 푸시 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Image: ${ECR_REGISTRY}/${ECR_REPOSITORY}"
echo -e "Tag: ${IMAGE_TAG}"
echo -e "Latest: latest"
echo ""
echo -e "${YELLOW}다음 명령어로 배포 스크립트를 실행하세요:${NC}"
echo -e "export IMAGE_TAG=${IMAGE_TAG}"
echo -e "./scripts/deploy-server.sh"
