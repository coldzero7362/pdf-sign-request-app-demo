# pdf-sign-request-app-demo

PDF에 입력 칸(텍스트, 날짜, 체크박스, 서명)을 지정해 외부 사용자에게 작성과 서명을 요청하는 서비스의 MVP 샘플입니다.

- 요청자: Google 로그인 → PDF 업로드 → 칸 배치 → 수신자 지정 → 발송
- 수신자: 메일 링크 → 이메일 OTP → 입력 → 제출 또는 거절
- 완료: 입력값을 PDF에 합성하고 감사 기록을 붙여 참여자에게 공유
- API: 개인 API 토큰(Bearer)으로 같은 기능 호출

## 구성

| 경로 | 내용 |
|---|---|
| `backend/` | Spring Boot 3.5, Java 21, Gradle |
| `frontend/` | React + Vite + TypeScript |
| `docker/Dockerfile` | 프런트 빌드 → 백엔드 jar에 포함 → 실행 이미지 |
| `docker-compose.yml` | 앱 컨테이너 (공용 MariaDB, Caddy 네트워크 사용) |

## 실행

```bash
cp .env.example .env   # 값 채우기
docker compose up -d --build
curl http://127.0.0.1:8002/api/v1/health
```

DB는 공용 MariaDB의 `docsign` 스키마를 사용하므로, 먼저 `~/infra/db`가 실행 중이어야 합니다.

## 개발

```bash
# 백엔드 테스트 (로컬 JDK 없이 Docker로)
docker run --rm -u $(id -u):$(id -g) -e GRADLE_USER_HOME=/cache \
  -v ~/.cache/pdfsign-gradle:/cache -v $PWD/backend:/work -w /work \
  gradle:8.14.3-jdk21 ./gradlew test --no-daemon

# 프런트 개발 서버 (/api는 BACKEND_URL로 프록시)
cd frontend && npm install && npm run dev
```

- API 문서: `/swagger-ui.html`
- 브랜치: `feature/*` → `develop` → `main`
