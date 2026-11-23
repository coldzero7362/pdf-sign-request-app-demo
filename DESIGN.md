---
version: 1
name: PDF 서명 요청 (USIGN 색 + Jobright 양식)
description: >
  화면 양식은 Jobright 디자인(getdesign `jobright`, 원본은 docs/design/jobright-original.md)을 따르고,
  색은 USIGN 팔레트(dev.usign.kr)를 쓴다. UI를 만들거나 고치기 전에 이 문서를 먼저 읽는다.
  구현은 frontend/src/app/globals.css 의 토큰이 기준이다.

colors:
  # USIGN 팔레트
  primary: "#1560FD"      # 대표 강조 (Jobright의 mint 자리)
  link: "#4468F9"
  header: "#345BF8"       # 로고
  navy: "#181A29"         # Jobright의 ink: 진한 알약 버튼, 어두운 띠
  text: "#111111"
  heading: "#242940"
  sub: "#555555"
  muted: "#808087"
  page-bg: "#F2F5F8"      # Jobright의 surface-soft
  surface: "#FFFFFF"
  border: "#E2E7EA"       # Jobright의 hairline
  success: "#2FC673"
  warning: "#FFA019"
  # 팔레트에 없어 USIGN과 비슷하게 채운 색
  tint: "#EAF1FF"         # Jobright의 canvas-mint(연한 구획 띠) 자리, Primary 계열
  tint-strong: "#D6E4FF"
  danger: "#D32F2F"
  ok-text: "#178049"      # Success 계열 글자색
  warn-text: "#A86400"    # Warning 계열 글자색

typography:
  heading: Titillium Web 700 (한글 Noto Sans KR)
  body: Inter 400 (한글 Noto Sans KR)
  h1: 38px / 1.2 (휴대폰 30px), 로그인 첫 화면 제목만 clamp(34px, 6vw, 56px)
  h2: 22px, 카드 20px, 패널 17px
  body-size: 15px / 1.6, 작은 글자 13px

rounded: { sm: 12px, md: 16px, lg: 24px, card: 40px, full: 9999px }
spacing: { xxs: 4px, xs: 8px, sm: 12px, md: 16px, lg: 24px, xl: 32px, xxl: 48px, section: 72px }
---

# 디자인 규칙

## 원칙
- **양식은 Jobright, 색은 USIGN.** Jobright 색(mint, 검정)은 쓰지 않고 위 표의 USIGN 색으로 바꿔 쓴다.
- 업무 화면이므로 Jobright 마케팅 사이트의 크기(제목 80px, 섹션 여백 96px)는 줄여 쓴다.
- 화면 코드에는 색 값을 직접 쓰지 않고 `globals.css`의 역할 토큰(`--ink`, `--accent`, `--band` …)만 쓴다.

## Jobright → USIGN 대응

| Jobright | 역할 | 이 프로젝트 |
|---|---|---|
| mint `#00f0a0` | 대표 강조 | Primary `#1560FD` |
| ink `#000000` | 진한 알약 버튼, 어두운 띠 | Navy `#181A29` |
| canvas-mint `#ecffe0` | 연한 구획 띠 | tint `#EAF1FF` (새로 정의) |
| surface-soft `#f2f4f5` | 연한 면 | Page BG `#F2F5F8` |
| hairline `#e5e7eb` | 테두리 | Border `#E2E7EA` |
| muted `#86909c` | 보조 글자 | Muted `#808087` / Sub `#555555` |

## 구성 요소

### 상단 바
- 흰 바탕, 아래 1px 테두리, 화면 위에 고정. 로고는 Titillium Web 700, Header 파랑.
- 오른쪽: 링크(내 요청, API 토큰), 이메일, 로그아웃, **Navy 알약 "+ 새 요청"**.
- 800px 이하: 링크는 ☰ 메뉴로 접고 "+ 새 요청" 알약만 남긴다.

### 버튼 (모두 알약)
| 클래스 | 모양 | 쓰는 곳 |
|---|---|---|
| `.btn-primary` | Navy 채움, 흰 글자 | 일반 주 동작 (새 요청, 토큰 발급, 편집) |
| `.btn-accent` | Primary 파랑 채움, 흰 글자 | **화면마다 하나뿐인 대표 동작** (Google 로그인, 올리고 칸 배치, 발송하기, 제출, 완료본 내려받기) |
| `.btn` | 흰 바탕, 테두리 | 보조 동작 |
| `.btn-ghost` | 테두리 없음 | 로그아웃, 취소, 지우기 |
| `.btn-danger` | 빨간 글자 | 삭제, 거절, 요청 취소 |

크기: 기본, `.btn-small`, `.btn-lg`, `.btn-block`(폭 전체).

### 구획 띠 (Jobright feature-band)
- `.band`: 화면 폭 전체의 연한 파랑(tint) 띠. 큰 제목, 안내 문구(`.lead`, 17px 300), 주 버튼을 둔다.
- 로그인 첫 화면과 목록 화면 위쪽에 쓴다. 목록 화면 띠에는 상태별 숫자 카드(`.stats`)를 둔다.

### 카드
- `.card`: 흰 바탕, 1px 테두리, 모서리 24px, 아주 옅은 그림자.
- `.card-feature`: 왼쪽 위만 각지고 나머지는 40px (Jobright의 비대칭 대표 카드). 로그인 단계 카드, 빈 목록 안내에 쓴다.
- `.panel`(편집기 옆 패널), `.dialog`: 모서리 16px / 24px.

### 입력칸
- 흰 바탕, 1px 테두리, 모서리 12px, 여백 12px 16px. 포커스 시 Primary 테두리.
- 라벨은 14px 600 Heading 색.

### 표 (Jobright data-table)
- 머리글 13px 500 Muted 색, 칸 여백 12px 16px, 줄 구분 1px 테두리, 마우스를 올리면 Page BG.
- 제목 링크는 Navy 굵게, 마우스를 올리면 Primary 밑줄.

### 배지 (알약)
| 상태 | 배경 | 글자 |
|---|---|---|
| 작성 중 | Page BG + 테두리 | Sub |
| 발송됨, 진행 중 | tint | Primary |
| 완료 | Success 14% | ok-text |
| 만료됨 | Warning 16% | warn-text |
| 거절됨, 취소됨 | danger 10% | danger |

### PDF 칸
- 편집기: 수신자별 색 테두리(1~4번: Primary, Warning, Success, Navy / 5~6번: Muted, `#0B7285`), 같은 색 13% 면, 모서리 4px (작은 체크 칸이 원처럼 보이지 않게).
- 수신자 화면: 작성할 칸은 Warning 점선, 작성한 칸은 Success 실선.

## 접근성 (흰 배경 대비, WCAG AA 4.5:1)
| 색 | 대비 | 처리 |
|---|---|---|
| Primary, Link, Header | 5.09, 4.57, 5.25 | 글자·버튼에 그대로 |
| Muted `#808087` | 3.92 | 작은 보조 문구는 Sub(7.46), Muted는 페이지 번호·표 머리글처럼 덜 중요한 곳에만 |
| Success `#2FC673` | 2.22 | 면·테두리에만. 글자는 ok-text `#178049`(4.98) |
| Warning `#FFA019` | 2.04 | 면·테두리에만. 글자는 warn-text `#A86400`(4.68) |
| danger `#D32F2F` | 4.98 | 글자에 그대로 |

## Jobright에서 바꾼 점
- **한글 본문 굵기 400**: Jobright 본문은 Inter 500이지만, 한글(Noto Sans KR)은 500이 무거워 보여 400을 쓴다.
- **한글 글꼴 보완**: Titillium Web, Inter에는 한글이 없어 Noto Sans KR을 뒤에 둔다.
- **글꼴은 같은 서버에서 제공**: `next/font`가 빌드할 때 내려받는다. 브라우저가 Google에 요청하지 않는다.
- **다크 모드 없음**: 두 디자인 모두 밝은 테마 기준이다.
- 스크롤 티커, 후기 띠, FAQ 같은 마케팅 요소는 쓰지 않는다.
