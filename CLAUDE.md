# PDF 서명 요청 앱 (MVP 샘플)

## 먼저 따를 것
- 업무 지침: @docs/GUIDELINES.md
  - 이 지침이 현재 구현보다 우선한다. 지침은 사용자가 지침 업데이트를 지시하거나 미정 사항에 답했을 때만 고친다.
  - 미정 사항은 임의로 정하지 말고 사용자에게 묻는다.
- 디자인 규칙: @DESIGN.md (UI 작업 전에 확인)
- 재설계 계획: `docs/REDESIGN-PLAN.md` (지침을 구현하는 단계와 설계. 작업 전에 확인하고, 진행하면서 갱신한다)
- 버전 선택 이유와 보안 설정: `docs/TECH-STACK.md` (버전을 바꾸면 함께 고친다)

## 구성
- `backend/`: Spring Boot 4.1, Java 25, Gradle 9 (패키지 `io.github.coldzero7362.pdfsign`)
- `frontend/`: Next.js 16 App Router, pdf.js legacy 빌드
- DB: 공용 MariaDB 11.4의 `docsign` 스키마 (`~/infra/db`), 마이그레이션은 Flyway (`backend/src/main/resources/db/migration`)
- 배포: `docker compose up -d --build` → `127.0.0.1:8002`, 공용 Caddy가 https://pdfsign.158-247-240-171.sslip.io 로 연결

## 빌드·테스트 (서버에 JDK·Node 없음, Docker로 실행)
```bash
# 백엔드 테스트
cd backend && docker run --rm -u 1001:1001 -e GRADLE_USER_HOME=/cache \
  -v ~/.cache/pdfsign-gradle:/cache -v $PWD:/work -w /work gradle:9.9.2-jdk25 ./gradlew test --no-daemon -q

# 프런트 lint·빌드 (/etc/passwd를 넘기지 않으면 npx가 실패함)
cd frontend && docker run --rm -u 1001:1001 -v /etc/passwd:/etc/passwd:ro -e HOME=/tmp \
  -e npm_config_cache=/npm -v ~/.cache/pdfsign-npm:/npm -v $PWD:/work -w /work node:26-slim \
  sh -c 'npm run lint && npm run build'
```

## 작업 규칙
- 브랜치: `develop`에서 `feature/*`를 따서 작업하고 `develop`에 `--no-ff`로 합친다.
- 비밀 값은 `.env`에만 둔다. 커밋하지 않는다.
