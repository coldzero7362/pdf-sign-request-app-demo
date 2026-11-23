# 재설계 계획

- 작성: 2026-11-23, 기준 지침: `docs/GUIDELINES.md` v6
- 이 문서는 지침을 **어떻게 구현할지**에 대한 계획입니다. 지침과 다르면 지침을 따르고, 이 문서를 고칩니다.
- 지침에 없는 내용을 제가 정한 곳은 **(제안)** 으로 표시했습니다. 시작 전에 확인할 것은 [9. 확인 필요](#9-확인-필요)에 모았습니다.

## 1. 바뀌는 큰 그림

| | 지금 | 재설계 후 |
|---|---|---|
| 서명 요청 | 웹에서 PDF 올리기 → 칸 배치 → 발송 | **API로만**: 템플릿 번호 + 수신자 정보 JSON |
| 양식 | 요청마다 새로 만듦 | **템플릿**을 웹에서 만들어 두고 재사용 |
| 진행 방식 | 모두 동시 | 템플릿에서 **순차 / 비순차** 선택 |
| 칸 종류 | 서명, 텍스트, 날짜, 체크 | + 요청자 고정 텍스트, 워터마크, 요청자·서명자 첨부 |
| 요청자 웹 | 요청 작성·편집·발송 | **보낸 문서 관리 + 템플릿 + API 토큰**만 |
| 종료 처리 | 거절, 취소 | **반려**, **회수**(기간 안), 만료, 완료 |
| 재발송 | 같은 문서 링크만 다시 보냄 | **수정 후 재발송 = 이력이 이어지는 새 문서** |
| 삭제 | 초안만 실제 삭제 | 종료 문서만 **숨기기** (열람기간·재발송 연결 시 불가) |

## 2. 지금 코드에서 살릴 것 / 바꿀 것 / 뺄 것

### 그대로 살림
- 인증: Google 로그인, API 토큰(PAT, 해시 저장, 스코프), 보안 체인 3개, CSRF, 세션 쿠키 설정 (`auth/`)
- 수신자 인증: 메일 링크 토큰 + 이메일 인증번호 + 세션 (`sign/SignAccessService`)
- 메일: Gmail SMTP, 커밋 후 발송, HTML 이스케이프 (`mail/`)
- 파일 저장소, PDF 검사, 이미지 정화 (`storage/`, `document/PdfInspector`, `sign/ImageSanitizer`)
- PDF 합성(회전 대응, 한글 글꼴)과 감사 기록 페이지 (`sign/PdfFlattener`) → 워터마크·고정 텍스트 추가
- 감사 기록 (`audit/`) → 문서 연결 정보 추가
- 프런트: PDF 렌더링(`pdf-pages`, `lib/pdf`), 칸 편집기(`field-editor`), 서명 패드, 디자인 시스템(`DESIGN.md`, `globals.css`)

### 바꿈
| 대상 | 바꾸는 내용 |
|---|---|
| `request/` (SignRequest 등) | "문서"로 재구성. 템플릿 스냅샷, 순차 진행, 회수·반려·재발송 연결, 숨기기 |
| `document/` (업로드한 PDF) | 템플릿에 딸린 파일로 이동 (`template/`) |
| `field-editor.tsx` | 요청 편집기 → **템플릿 편집기**. 수신자 자리, 새 칸 종류, 워터마크, 옵션 |
| `/sign/[token]` 수신자 화면 | 반려 사유, 서명자 첨부 올리기, 고정 텍스트·워터마크 표시, 순차 대기 안내 |
| `/` 목록, `/requests/[id]` 상세 | **보낸 문서 관리**로 확장 (필터·검색·기간, 숨긴 문서, 회수, 재발송, 다운로드) |
| 메일 재발송 API | **웹 전용**으로 제한 (API 토큰으로는 호출 불가, 지침 19) |

### 뺌
- 웹의 새 요청 화면 `/requests/new`, 요청 편집 `/requests/[id]/edit`
- 초안(`DRAFT`) 상태와 초안 삭제 API
- 요청 단위 PDF 업로드 API (`POST /api/v1/documents` 현재 형태)

## 3. 데이터 모델

### 3.1 템플릿 (요청자가 웹에서 관리)
| 테이블 | 주요 컬럼 | 설명 |
|---|---|---|
| `templates` | id, `template_no`, owner_id, name, signing_mode(`SEQUENTIAL`/`PARALLEL`), default_period_days(기본 14), pdf 파일 정보, version, deleted_at | 템플릿 번호 형식은 미정이라 `template_no`를 따로 두어 나중에 형식만 바꿀 수 있게 함 |
| `template_pages` | template_id, page_no, width, height, rotation | 지금 `document_pages`와 같음 |
| `template_slots` | id, template_id, slot_key("signer1"), label("서명자 1"), order_no | 수신자 자리. 순차일 때 order_no 순서로 진행 |
| `template_fields` | id, template_id, slot_id(요청자 고정값이면 null), type, page, x, y, w, h, required, label, fixed_value | type: `SIGNATURE`, `DATE`, `TEXT`, `TEXT_FIXED`, `CHECKBOX`, `ATTACHMENT` |
| `template_watermarks` | template_id, kind(`TEXT`/`IMAGE`), text, image 파일, x, y, w, h, opacity, rotation, font_size, color | 모든 페이지에 같은 좌표·크기 (지침 15) |
| `template_files` | id, template_id, file 정보, name, size, content_type | 요청자 첨부 (5개, 합계 10MB) |

- **서명자 첨부**는 `ATTACHMENT` 칸으로 표현합니다. 어느 자리가 올릴지, 필수 여부, 안내 문구를 칸처럼 정합니다. 화면에서는 문서 위가 아니라 별도 목록으로 보여 줍니다. **(제안)**
- **템플릿 수정과 이미 보낸 문서**: 발송할 때 템플릿 내용을 문서에 **복사(스냅샷)** 합니다. 템플릿을 고쳐도 이미 보낸 문서는 바뀌지 않습니다. 템플릿에는 `version`을 두고 문서에 어느 버전으로 보냈는지 남깁니다. **(제안)**

### 3.2 문서 (API로 만든 서명 요청 한 건)
| 테이블 | 주요 컬럼 | 설명 |
|---|---|---|
| `documents` | id, public_id, owner_id, template_id, template_version, title, message, signing_mode, status, expires_at, sent_at, completed_at, rejected_at, recalled_at, hidden_at, **previous_document_id**, 완료본 파일·해시 | `previous_document_id`로 재발송 연결 (지침 13) |
| `document_recipients` | id, document_id, role(`SIGNER`/`VIEWER`), slot_key, order_no, email, name, status, 링크 토큰 해시, OTP 정보, viewed_at, signed_at, rejected_at, reject_reason | 지금 `recipients`와 거의 같음 |
| `document_fields` | 템플릿 칸 스냅샷 + value, file_key, filled_at | 지금 `sign_fields`와 거의 같음 |
| `document_watermarks` | 템플릿 워터마크 스냅샷 | |
| `document_files` | id, document_id, kind(`REQUESTER`/`SIGNER`), recipient_id, field_id, file 정보 | 요청자 첨부 복사본 + 서명자가 올린 파일 |
| `audit_events` | + related_document_id | 재발송 연결을 양쪽 문서 기록에 남김 |

### 3.3 상태
| 문서 상태 | 뜻 | 종료? |
|---|---|---|
| `IN_PROGRESS` | 발송됨, 서명 진행 중 | 아니오 |
| `COMPLETED` | 모든 서명자 완료 | 예 |
| `REJECTED` | 서명자가 반려 (지침 5) | 예 |
| `RECALLED` | 요청자가 회수 (지침 6) | 예 |
| `EXPIRED` | 서명 요청 기간이 지남 | 예 |

| 서명자 상태 | 뜻 |
|---|---|
| `WAITING` | 순차 진행에서 앞사람 차례를 기다림 (아직 메일 안 감) |
| `REQUESTED` | 요청 메일을 받음 |
| `VIEWED` | 문서를 열어 봄 |
| `SIGNED` | 서명 완료 |
| `REJECTED` | 반려 |

참조자는 `WAITING` → (완료 시) `NOTIFIED`.

### 3.4 기존 데이터
지금 DB에는 테스트로 만든 요청 데이터만 있습니다. 새 구조로 옮기지 않고, 요청 관련 테이블은 새로 만듭니다. 사용자와 API 토큰은 그대로 둡니다. **(제안, 확인 필요 1)**

## 4. API

모든 API는 `/api/v1`. 인증은 API 토큰(Bearer) 또는 웹 세션입니다. 웹 화면도 같은 API를 씁니다 (지침 6, 20).

### 4.1 템플릿 (웹에서 관리, API로는 조회만 **(제안)**)
| 메서드 | 경로 | 설명 | API 토큰 |
|---|---|---|---|
| GET | `/templates` | 내 템플릿 목록 (번호, 이름, 자리 목록) | 조회 가능 (`templates:read`) |
| GET | `/templates/{templateNo}` | 템플릿 상세 (자리, 칸, 옵션) | 조회 가능 |
| POST | `/templates` | 템플릿 만들기 (PDF 올리기) | 웹 전용 |
| PUT | `/templates/{templateNo}` | 이름, 순차/비순차, 기본 기간, 자리, 칸, 워터마크 저장 | 웹 전용 |
| POST/DELETE | `/templates/{templateNo}/files` | 요청자 첨부 올리기·빼기 | 웹 전용 |
| DELETE | `/templates/{templateNo}` | 템플릿 삭제(숨김). 이미 보낸 문서에는 영향 없음 | 웹 전용 |

API 사용자가 어떤 자리(`slot_key`)에 누구를 넣어야 하는지 알 수 있도록 조회만 열어 둡니다.

### 4.2 문서
| 메서드 | 경로 | 설명 | API 토큰 |
|---|---|---|---|
| POST | `/documents` | **서명 요청** (아래 예시) | `documents:write` |
| GET | `/documents` | 목록. `status`, `q`(제목·이메일), `from`, `to`, `hidden` | `documents:read` |
| GET | `/documents/{id}` | 상세. 서명자별 상태, 감사 추적, 이어진 문서, `resend` 블록 (지침 20) | `documents:read` |
| POST | `/documents/{id}/recall` | 회수 (진행 중 + 기간 안) | `documents:write` |
| POST | `/documents/{id}/resend` | 수정 후 재발송 → 새 문서 (바꿀 값만) | `documents:write` |
| GET | `/documents/{id}/result` | 완료 PDF | `documents:read` |
| GET | `/documents/{id}/files/{fileId}` | 요청자·서명자 첨부 내려받기 | `documents:read` |
| POST | `/documents/{id}/recipients/{rid}/remind` | 요청 메일 다시 보내기 | **웹 전용** (지침 19) |
| POST | `/documents/{id}/hide` · `/unhide` | 숨기기 · 되돌리기 | 웹 전용 **(제안)** |

#### 서명 요청 예시
```json
POST /api/v1/documents
Idempotency-Key: 7f3c2b2e-...            (선택, 같은 요청을 두 번 보내도 한 건만 생김 — 제안)
{
  "templateNo": "T-0001",
  "title": "2026년 근로계약서 - 홍길동",
  "message": "확인 후 서명 부탁드립니다.",
  "periodDays": 14,
  "signers": [
    { "slot": "signer1", "email": "hong@example.com", "name": "홍길동" },
    { "slot": "signer2", "email": "hr@example.com",   "name": "인사팀장" }
  ],
  "viewers": [
    { "email": "ceo@example.com", "name": "대표" }
  ]
}
```
- `signers`는 템플릿의 모든 자리를 채워야 합니다.
- `viewers`(참조자)는 템플릿 자리와 상관없이 자유롭게 넣습니다. **(제안, 확인 필요 2)**
- `periodDays`가 없으면 템플릿 기본값(기본 14일), 최대 31일 (지침 17).
- 응답: 문서 번호, 상태, 서명자별 상태.

#### 재발송 예시 (지침 20)
```json
GET /api/v1/documents/{id}
→ { ..., "resend": { "available": true, "request": { "templateNo": "T-0001", "title": "...", "signers": [...], "viewers": [...], "periodDays": 14 } } }

POST /api/v1/documents/{id}/resend
{ "signers": [ { "slot": "signer2", "email": "hr2@example.com", "name": "새 담당자" } ] }
→ 새 문서. 보내지 않은 값은 원래 문서에서 가져옴. previous_document_id = {id}
```
- 바꾸는 `signers`는 자리(`slot`) 단위로 덮어씁니다. **(제안)**

### 4.3 수신자 (`/sign/api`, 계정 없음)
지금 구조를 유지하고 다음을 더합니다.
- 반려 (`/reject`, 사유 선택 입력) — 지금 `/decline`을 이름만 바꿈
- 서명자 첨부 올리기·빼기·목록 (`/files`) — 5개, 합계 10MB, 문서·사진 형식
- 요청자 첨부 내려받기
- 순차일 때 차례가 아닌 서명자는 들어올 수 없음 (메일도 안 감)

### 4.4 API 토큰 스코프 **(제안)**
`requests:read/write` → `documents:read`, `documents:write`, `templates:read`로 바꿉니다. 기존 토큰은 테스트용이라 새로 발급합니다.

## 5. 처리 규칙

| 상황 | 처리 |
|---|---|
| 발송 | 템플릿 스냅샷을 만들고, 비순차면 모든 서명자에게, 순차면 첫 서명자에게만 메일 (지침 9) |
| 서명 완료 (순차) | 다음 순서 서명자를 `REQUESTED`로 바꾸고 메일 |
| 모든 서명 완료 | 입력값·고정 텍스트·워터마크를 PDF에 합성 + 감사 기록 페이지. 서명자 첨부는 넣지 않음 (지침 16). 서명자·참조자·요청자에게 완료 링크 메일 (지침 12) |
| 반려 | 즉시 `REJECTED`. 아직 차례가 안 온 서명자에게는 메일을 보내지 않음. 이미 요청을 받은 다른 서명자와 요청자에게 알림 (지침 5) |
| 회수 | 진행 중이고 기간 안일 때만. `RECALLED`. 요청을 받은 서명자에게 알림 (지침 6) |
| 만료 | 기간이 지나면 `EXPIRED` (접근할 때 + 10분마다) |
| 재발송 | 반려·회수된 문서만. 새 문서를 만들고 양쪽 감사 추적에 연결 기록 (지침 13, 20) |
| 숨기기 | 종료 문서만. 완료 문서는 완료 후 30일이 지나야. 재발송으로 이어진 문서는 불가 (지침 22) |
| 워터마크 표시 | 서명 화면(pdf.js 위 겹쳐 그리기)과 완료 PDF(PDFBox) 양쪽에 같은 좌표·크기·투명도·기울기로 그림 |
| 첨부 검사 | 확장자뿐 아니라 파일 내용(시그니처)으로 형식 확인. HWP는 OLE/ZIP(HWPX) 구조 확인. 미리보기 없이 내려받기만 |

## 6. 화면

### 요청자 (로그인)
| 화면 | 내용 |
|---|---|
| 보낸 문서 목록 | 상태 필터, 제목·이메일 검색, 기간, "숨긴 문서 보기" 전환, 상태별 숫자 |
| 문서 상세 | 서명자별 진행(순서 표시), 감사 추적(재발송으로 이어진 문서 링크), 완료본·첨부 내려받기, 회수, 수정 후 재발송 양식, 요청 메일 다시 보내기, 숨기기 |
| 템플릿 목록 | 템플릿 번호, 이름, 자리 수, 최근 수정일. 번호 복사 버튼 (API에 넣을 값) |
| 템플릿 편집기 | 지금 칸 편집기를 바탕으로: PDF, 수신자 자리 관리(순서 끌어 바꾸기), 칸 배치(새 종류 포함), 워터마크 편집(문구·이미지, 위치·크기·투명도·기울기, 전체 페이지 미리보기), 옵션(순차/비순차, 기본 기간), 요청자 첨부 |
| API 토큰 | 지금 화면 + 템플릿 번호로 요청하는 예시 코드 |

### 서명자·참조자 (계정 없음)
| 화면 | 내용 |
|---|---|
| 서명 화면 | 지금 화면 + 워터마크 표시, 고정 텍스트 표시, 요청자 첨부 내려받기, 서명자 첨부 올리기, 반려(사유) |
| 대기 안내 | 순차에서 차례가 아닌 경우 (링크를 받기 전이라 보통 볼 일 없음) |
| 완료 문서 | 인증번호 → 완료 PDF 내려받기 (지금과 같음) |

## 7. 작업 순서

각 단계마다 테스트를 쓰고, 배포해서 확인한 뒤 `develop`에 합칩니다.

| 단계 | 내용 | 결과물 |
|---|---|---|
| 1 | 템플릿 도메인과 API, 템플릿 편집기 (기본 칸 + 수신자 자리 + 순차/비순차 + 기본 기간) | 웹에서 템플릿을 만들고 번호를 확인 |
| 2 | 서명 요청 API (템플릿 스냅샷, 순차/비순차 발송, 기간), 새 문서 테이블 | API로 요청 → 메일 발송 |
| 3 | 수신자 화면 갱신 (순차 진행, 반려, 고정 텍스트) | 순차·비순차 서명 끝까지 |
| 4 | 완료 처리 (고정 텍스트 합성, 요청자 포함 완료 링크 메일) | 완료 PDF와 메일 |
| 5 | 회수, 조회 API `resend` 블록, 재발송 API, 감사 추적 연결 | 회수 → 수정 → 재발송 흐름 |
| 6 | 보낸 문서 관리 웹 (필터·검색·기간, 상세, 회수, 재발송 양식, 메일 다시 보내기 웹 전용, 숨기기·숨긴 문서) | 지침 19 전체 |
| 7 | 워터마크 (편집기, 서명 화면 표시, 완료 PDF 합성) | 지침 15 |
| 8 | 첨부파일 (요청자 첨부, 서명자 첨부, 권한, 제한, 형식 검사) | 지침 16 |
| 9 | 정리: 옛 화면·API 제거, `docs/API.md`·`README.md` 갱신, 전체 E2E, `main` 릴리스 | v0.2.0 |

1~6이 핵심 흐름이고, 7~8은 독립 기능이라 순서를 바꿀 수 있습니다.

## 8. 주의할 점
- **템플릿과 문서 분리**: 스냅샷을 빠뜨리면 템플릿을 고칠 때 진행 중인 문서가 바뀝니다. 테스트로 막습니다.
- **순차 진행과 동시 처리**: 같은 서명자가 두 창에서 제출하거나, 반려와 제출이 동시에 일어날 수 있습니다. 문서에 낙관적 잠금(`version`)을 걸어 한쪽만 성공하게 합니다.
- **메일 양**: Gmail은 하루 약 500통입니다. 순차 진행과 완료 메일(서명자 + 참조자 + 요청자)로 늘어나므로, 샘플 수준에서는 충분하지만 실제 운영에는 전용 발송 서비스가 필요합니다.
- **첨부파일 보안**: 바이러스 검사는 하지 않습니다. 내려받을 때 `Content-Disposition: attachment`와 `nosniff`로 브라우저가 실행하지 않게 합니다. 서명자 첨부는 요청자 본인만 내려받을 수 있게 매 요청마다 소유자를 확인합니다.
- **워터마크 일치**: 화면(pdf.js)과 완료 PDF(PDFBox)는 그리는 방식이 달라 글꼴 크기와 기울기 기준점이 어긋나기 쉽습니다. 기준점(칸의 중심)과 단위(pt)를 하나로 정하고, 완료 PDF를 다시 렌더링해 비교하는 테스트를 둡니다.
- **API 호환성**: 경로와 스코프가 바뀝니다. 지금 API를 쓰는 외부 연동은 없다고 보고 한 번에 바꿉니다.

## 9. 확인 필요
시작 전에 정해 주시면 좋은 것입니다. 1~3은 1단계 전에 필요합니다.

1. **기존 테스트 데이터**: 지금까지 만든 요청·문서 데이터는 버리고 새 구조로 시작해도 되나요? (사용자와 API 토큰은 유지)
2. **참조자 지정**: 참조자는 템플릿 자리 없이 요청 JSON에서 자유롭게 넣는 방식이면 될까요?
3. **템플릿 번호 형식** (지침 미정 1): 정하기 전까지 `T-0001`처럼 요청자별 순번으로 임시 운영하고, 형식만 나중에 바꿀 수 있게 만들어도 될까요?
4. **템플릿 API**: API 토큰으로 템플릿 **조회**는 허용하고, 만들기·수정은 웹에서만 하는 것이 괜찮나요?
5. **숨김 되돌리기** (지침 미정 3): 숨긴 문서를 다시 목록으로 되돌리는 기능도 넣을까요?
6. **반려·회수 알림**: 반려·회수 때 이미 요청을 받은 다른 서명자에게 알림 메일을 보낼까요? (지금 계획은 보냄)
