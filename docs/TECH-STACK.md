# 기술 스택과 버전 선택 이유

검토일: 2026-11-23

선택 기준은 세 가지입니다.

1. **보안 패치를 받을 수 있는가**: 무료(OSS) 지원 기간 안에 있는 버전만 쓴다.
2. **알려진 취약점이 없는가**: GitHub Advisory와 `npm audit`로 확인한다.
3. **안정적인가**: 막 나온 `.0` 버전보다 패치가 몇 번 나온 버전을 우선한다.

## 요약

| 영역 | 선택 | 지원 기간 (무료) |
|---|---|---|
| Java | **25 LTS** (Eclipse Temurin) | LTS, 최소 2030년까지 |
| Spring Boot | **4.1.3** (2026-11-19) | 2027-07-31 |
| Spring Security | 7.x (Boot 4.1 관리 버전) | Boot와 같음 |
| Gradle | **9.9.2** | 최신 안정판 |
| springdoc-openapi | **3.2.1** | Boot 4용 3.x 라인 |
| MariaDB | **11.4 LTS** (기존 공용 DB) | 2029-05 |
| Node.js | **26 LTS** | Active LTS, 2029-04 종료 |
| Next.js | **16.4.6** (2026-11-09) | 최신 안정판 |
| React | 19.3.0 (Next 16.4.6 기본 조합) | 최신 라인 |

## 백엔드

### Spring Boot 4.1.3 (3.5에서 변경)
- 처음에는 Spring Boot 3을 기준으로 했지만, **3.5.x의 무료 지원이 2026-06-30에 끝났습니다.** 마지막 무료 패치는 3.5.16(2026-06)이고, 이후 Spring Boot·Spring Security 취약점은 유료 지원이 있어야 고쳐집니다.
- 4.0.x도 무료 지원이 2026-12-31에 끝나므로 제외했습니다.
- 4.2.0은 이제 막 나온 `.0` 버전이라, 패치가 세 번 나온 4.1.3을 골랐습니다. 4.1 → 4.2는 마이너 업그레이드라 나중에 올리기 쉽습니다.
- Boot 4로 오면서 바뀐 점:
  - 스타터를 기능별로 나눔: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-starter-webmvc-test` 등
  - Spring Security 7, Hibernate 7, Jakarta EE 11, Jackson 3
  - springdoc은 Boot 4용인 **3.x**를 써야 합니다. 2.x는 Boot 3용입니다.

### Java 25 LTS
- 2025-09에 나온 최신 LTS로, 21보다 지원 기간이 깁니다.
- Spring Boot 4.1과 Gradle 9.9가 모두 Java 25를 지원합니다.
- 가상 스레드를 쓸 수 있고, 스레드 고정(pinning) 문제가 21보다 줄었습니다 (JEP 491, Java 24부터).

### Gradle 9.9.2
- Java 25에서 빌드하려면 Gradle 9.1 이상이 필요합니다. 그래서 최신 안정판을 썼습니다.
- wrapper를 저장소에 넣어 두어, 서버에 Gradle을 따로 설치하지 않고 같은 버전으로 빌드합니다.

### MariaDB 11.4 LTS
- 서버에 이미 운영 중인 공용 DB입니다. 이 앱 전용 스키마 `docsign`과 전용 계정을 따로 둡니다.
- 11.4는 LTS(2029-05까지)이고, `UUID` 타입과 `JSON` 검사를 지원해 설계에 필요한 기능이 다 있습니다.
- JDBC 드라이버는 Spring Boot가 관리하는 버전을 씁니다.

## 프런트엔드

### Next.js 16.4.6
- 2026년에 Next.js에서 **인증 없이 원격 코드 실행(RCE)이 가능한 심각한 취약점**이 여러 번 나왔습니다.

  | 권고 | 영향 범위 | 공격 지점 |
  |---|---|---|
  | GHSA-673q-mjm6-r5xr | 16.0.0 ~ 16.4.0 | `use cache` 함수 |
  | GHSA-2464-fhr4-w9xw | 10.0.0 ~ 15.5.27, 16.0.0 ~ 16.4.0 | 이미지 최적화 API (SVG) |
  | GHSA-vcvr-r3jv-pc5j | 16.2.0 ~ 16.3.5 | `next/og` ImageResponse |
  | GHSA-2xp9-vwfh-vxw4 | ~15.5.23, 16.0.0 ~ 16.3.2 | 이미지 최적화 API (AVIF) |

- 그래서 **16.4.1 이상이 최소 조건**이고, 그중 최신 패치인 16.4.6을 썼습니다. 이 버전은 검토일 기준 공개된 권고가 0건이고, `npm audit` 결과도 0건입니다.
- 15.5.x 백포트 라인도 패치는 나오지만, 새 프로젝트이므로 최신 메이저를 택했습니다.
- App Router, TypeScript, ESLint를 쓰고 Tailwind는 넣지 않았습니다. 샘플이라 의존성을 줄이려는 것입니다.

### Node.js 26 LTS
- 2026-10부터 Active LTS이고 2029-04까지 지원됩니다.
- Node 22는 유지보수 단계이고 2027-04에 끝나므로, 새 프로젝트에는 26을 썼습니다.

## 적용한 보안 설정

### Next.js (`frontend/next.config.ts`)
- `images.unoptimized: true`: 이미지 최적화 API(`/_next/image`)를 끕니다. 올해 RCE가 두 번 나온 곳이고, 이 앱에는 필요 없습니다.
- `poweredByHeader: false`: `X-Powered-By` 헤더를 빼서 프레임워크 정보를 노출하지 않습니다.
- 공통 보안 헤더: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`
- `output: "standalone"`: 실행에 필요한 파일만 이미지에 넣어 공격 표면과 이미지 크기를 줄입니다.
- 코딩 규칙: **`use cache` 지시어와 `next/og`는 쓰지 않습니다.** 필요해지면 그때 최신 권고를 다시 확인합니다.
- 서드파티 폰트(Google Fonts)를 빼서 빌드할 때나 실행할 때 외부 요청이 없게 했습니다.

### Spring Boot
- 보안 기본값은 **모두 차단**이고, 헬스 체크와 API 문서만 공개합니다.
- JPA `ddl-auto: validate`: 스키마는 Flyway로만 바꾸고, Hibernate가 테이블을 고치지 못하게 합니다.
- `open-in-view: false`: 화면 렌더링 중에 지연 로딩 쿼리가 나가지 않게 합니다.

### 컨테이너
- 두 컨테이너 모두 root가 아닌 사용자로 실행합니다 (api는 `app`, web은 `node`).
- 화면만 `127.0.0.1:8002`에 열고, api는 외부 포트 없이 내부 네트워크로만 접근합니다.
- 비밀 값은 `.env`로만 넘기고 저장소와 이미지에는 넣지 않습니다.

### 다음 단계에서 붙일 것
- Content-Security-Policy (nonce 방식)
- 로그인 이후 세션 쿠키: `Secure`, `HttpOnly`, `SameSite=Lax`
- CSRF 보호 (웹 체인), Bearer 토큰 체인은 stateless
- 업로드 PDF 크기와 형식 검증, 요청 횟수 제한

## 버전 관리 방침
- `.github/dependabot.yml`로 Gradle, npm, Docker 이미지 업데이트를 매주 확인합니다.
- 보안 권고가 나오면 패치 버전은 바로 올리고, 마이너 이상은 이 문서를 고친 뒤 올립니다.
- 다음 점검 시점: Spring Boot 4.1 무료 지원 종료(2027-07-31) 전에 4.2 이상으로 올립니다.
