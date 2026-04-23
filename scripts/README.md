# Docker + ECR 배포 가이드

현재 브랜치를 기준으로 Docker 이미지를 빌드하여 ECR에 푸시하고, 서버에서 Docker Compose로 배포하는 가이드입니다.

## 사전 준비

### 1. AWS 자격 증명 설정x
```bash
# AWS CLI 설치 및 구성
aws configure
```

### 2. 환경 변수 설정
```bash
# ECR 정보
export AWS_REGION="ap-northeast-2"
export ECR_REGISTRY="219268921033.dkr.ecr.ap-northeast-2.amazonaws.com"
export ECR_REPOSITORY="imyme-backend"

# 버전 지정 (선택사항)
export VERSION="1.0.0"  # 지정하지 않으면 자동으로 브랜치-커밋-타임스탬프 형식 사용

# 서버 정보
export SERVER_HOST="3.39.22.186"
export SERVER_USER="ubuntu"
export SSH_KEY_PATH="/Users/wonhyeonseob/Desktop/git/MINE/AWS/KEY/mine.pem"
export SPRING_PROFILES_ACTIVE="prod"
```

### 3. ECR 리포지토리 생성 (최초 1회)
```bash
aws ecr create-repository \
    --repository-name imyme-backend \
    --region ap-northeast-2
```

### 4. 서버에 .env 파일 준비
서버의 `/home/ubuntu/mine/backend/.env` 파일에 환경 변수를 설정하세요:
```bash
# 데이터베이스
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/imyme
SPRING_DATASOURCE_USERNAME=your_user
SPRING_DATASOURCE_PASSWORD=your_password

# JWT
JWT_SECRET=your_jwt_secret

# AWS S3
AWS_S3_BUCKET_NAME=your-bucket
AWS_ACCESS_KEY_ID=your_key
AWS_SECRET_ACCESS_KEY=your_secret

# OAuth
GOOGLE_CLIENT_ID=your_client_id
GOOGLE_CLIENT_SECRET=your_client_secret
GOOGLE_REDIRECT_URI=your_redirect_uri
```

## 배포 방법

### 방법 1: 스크립트 사용 (권장)

#### 1단계: ECR에 이미지 푸시
```bash
# 버전 지정 방식 (권장)
export VERSION="1.0.0"
./scripts/deploy-ecr.sh

# 또는 자동 태그 방식
./scripts/deploy-ecr.sh  # main-7eada42-20260210-163701 형식으로 자동 생성
```

#### 2단계: 서버에 배포
```bash
# 버전을 지정했다면
export IMAGE_TAG="1.0.0"
./scripts/deploy-server.sh

# 또는 자동 태그를 사용했다면
export IMAGE_TAG="main-7eada42-20260210-163701"  # 1단계에서 출력된 태그
./scripts/deploy-server.sh
```

이 스크립트는:
- docker-compose.yml 파일을 서버에 전송
- 서버에서 ECR 로그인
- 새 이미지 pull
- 기존 컨테이너 중지
- 새 컨테이너 시작
- 헬스체크 수행
- 사용하지 않는 이미지 정리

### 방법 2: 수동 배포

#### 1단계: 로컬에서 이미지 빌드 및 푸시
```bash
# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | \
    docker login --username AWS --password-stdin ${ECR_REGISTRY}

# 이미지 빌드
BRANCH=$(git rev-parse --abbrev-ref HEAD)
COMMIT=$(git rev-parse --short HEAD)
IMAGE_TAG="${BRANCH}-${COMMIT}-$(date +%Y%m%d-%H%M%S)"

docker build -t ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG} .
docker tag ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG} \
           ${ECR_REGISTRY}/${ECR_REPOSITORY}:${BRANCH}-latest

# 이미지 푸시
docker push ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}
docker push ${ECR_REGISTRY}/${ECR_REPOSITORY}:${BRANCH}-latest
```

#### 2단계: 서버에 배포
```bash
# docker-compose.yml 전송
scp docker-compose.yml ${SERVER_USER}@${SERVER_HOST}:/home/${SERVER_USER}/mine/backend/

# 서버 접속하여 배포
ssh ${SERVER_USER}@${SERVER_HOST}

# 서버에서 실행
cd /home/ubuntu/mine/backend

# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | \
    docker login --username AWS --password-stdin ${ECR_REGISTRY}

# 환경 변수 설정
export ECR_REGISTRY="219268921033.dkr.ecr.ap-northeast-2.amazonaws.com"
export ECR_REPOSITORY="imyme-backend"
export IMAGE_TAG="main-abc1234-20260210-143000"
export SPRING_PROFILES_ACTIVE="prod"

# 배포
docker-compose down
docker-compose pull
docker-compose up -d

# 로그 확인
docker-compose logs -f
```

## 유용한 명령어

### 로컬
```bash
# 로컬에서 Docker 이미지 테스트
docker build -t imyme-backend:test .
docker run -p 8080:8080 --env-file .env imyme-backend:test

# 이미지 크기 확인
docker images | grep imyme-backend
```

### 서버
```bash
# 컨테이너 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs -f
docker-compose logs --tail=100 backend

# 컨테이너 재시작
docker-compose restart

# 컨테이너 중지
docker-compose down

# 사용하지 않는 이미지/컨테이너 정리
docker system prune -a
```

## 트러블슈팅

### 1. ECR 로그인 실패
```bash
# AWS 자격 증명 확인
aws sts get-caller-identity

# ECR 리포지토리 확인
aws ecr describe-repositories --region ap-northeast-2
```

### 2. 헬스체크 실패
```bash
# 컨테이너 로그 확인
docker-compose logs backend

# 컨테이너 내부 접속
docker exec -it imyme-backend sh

# 헬스체크 엔드포인트 직접 호출
curl http://localhost:8080/health
```

### 3. 이미지 pull 실패
```bash
# 서버에서 ECR 로그인 재시도
aws ecr get-login-password --region ap-northeast-2 | \
    docker login --username AWS --password-stdin ${ECR_REGISTRY}

# 이미지 존재 확인
aws ecr describe-images \
    --repository-name imyme-backend \
    --region ap-northeast-2
```

## CI/CD 통합

GitHub Actions에 통합하려면 [prod.yml](.github/workflows/prod.yml)에 다음 단계를 추가하세요:

```yaml
- name: Build and Push to ECR
  run: |
    aws ecr get-login-password --region ${{ secrets.AWS_REGION }} | \
        docker login --username AWS --password-stdin ${{ secrets.ECR_REGISTRY }}

    docker build -t ${{ secrets.ECR_REGISTRY }}/${{ secrets.ECR_REPOSITORY }}:${GITHUB_SHA} .
    docker push ${{ secrets.ECR_REGISTRY }}/${{ secrets.ECR_REPOSITORY }}:${GITHUB_SHA}

- name: Deploy to Server
  run: |
    ssh ubuntu@${{ secrets.SERVER_HOST }} 'bash -s' <<'EOF'
      cd /home/ubuntu/mine/backend
      export IMAGE_TAG=${GITHUB_SHA}
      docker-compose pull
      docker-compose up -d
    EOF
```

## 참고사항

- 이미지 태그는 `{branch}-{commit}-{timestamp}` 형식을 사용합니다
- `{branch}-latest` 태그는 항상 해당 브랜치의 최신 이미지를 가리킵니다
- 서버에는 최대 3개의 릴리스 디렉토리가 유지됩니다 (롤백용)
- Docker 이미지는 multi-stage build를 사용하여 크기를 최적화합니다
