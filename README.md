# pdf-sign-request-app-demo

PDF에 입력 칸(텍스트, 날짜, 체크박스, 서명)을 지정해 외부 사용자에게 작성과 서명을 요청하는 서비스의 MVP 샘플입니다.

- 요청자: Google 로그인 → PDF 업로드 → 칸 배치 → 수신자 지정 → 발송
- 수신자: 메일 링크 → 이메일 OTP → 입력 → 제출 또는 거절
- 완료: 입력값을 PDF에 합성하고 감사 기록을 붙여 참여자에게 공유
- API: 개인 API 토큰(Bearer)으로 같은 기능 호출

## 구성

| 경로 | 내용 |
|---|---|
| `backend/` | Spring Boot 4.1, Java 25, Gradle 9 |
| `frontend/` | Next.js 16 (App Router) + TypeScript |
| `docker/api.Dockerfile` | API 이미지 |
| `docker/web.Dockerfile` | 화면 이미지 (Next.js standalone) |
| `docker-compose.yml` | `api` + `web` 컨테이너 (공용 MariaDB, Caddy 네트워크 사용) |
| `docs/TECH-STACK.md` | 버전 선택 이유와 보안 설정 |

## 실행

```bash
cp .env.example .env   # 값 채우기
docker compose up -d --build
curl http://127.0.0.1:8002/api/v1/health   # web이 api로 넘겨 줌
```

DB는 공용 MariaDB의 `docsign` 스키마를 사용하므로, 먼저 `~/infra/db`가 실행 중이어야 합니다.

## 접속

- 서비스: https://pdfsign.158-247-240-171.sslip.io
- API 문서: https://pdfsign.158-247-240-171.sslip.io/swagger-ui.html

## API 토큰으로 호출하기

1. 화면에서 Google로 로그인한 뒤 **API 토큰** 메뉴에서 토큰을 발급합니다. 원문은 발급할 때 한 번만 보입니다.
2. 요청 헤더에 넣어 호출합니다.

```bash
curl -H "Authorization: Bearer pst_..." https://pdfsign.158-247-240-171.sslip.io/api/v1/me
```

토큰 발급과 폐기는 보안상 브라우저 로그인 상태에서만 할 수 있습니다.

## 개발

```bash
# 백엔드 테스트 (로컬 JDK 없이 Docker로)
docker run --rm -u $(id -u):$(id -g) -e GRADLE_USER_HOME=/cache \
  -v ~/.cache/pdfsign-gradle:/cache -v $PWD/backend:/work -w /work \
  gradle:9.9.2-jdk25 ./gradlew test --no-daemon

# 프런트 개발 서버 (/api 등은 BACKEND_URL, 기본 http://localhost:8080으로 넘김)
cd frontend && npm install && npm run dev
```

- API 문서: `/swagger-ui.html`
- 브랜치: `feature/*` → `develop` → `main`
