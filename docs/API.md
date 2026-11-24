# API 사용 안내

전체 목록과 요청·응답 형식은 Swagger에서 볼 수 있습니다: `/swagger-ui.html`
업무 규칙은 `docs/GUIDELINES.md`를 따릅니다.

## 흐름 한눈에 보기

```
[요청자 웹] 템플릿 만들기 (PDF + 수신자 자리 + 칸 + 순차/비순차 + 기본 기간) → 번호 T-0001
[요청자 시스템] POST /api/v1/documents  (템플릿 번호 + 자리별 서명자 + 참조자)
   → 비순차: 모든 서명자에게 요청 메일 / 순차: 첫 서명자에게만
[서명자] 메일 링크 → 이메일 인증번호 → 칸 작성 → 서명 완료 (또는 반려)
   → 순차: 다음 서명자에게 요청 메일
[모두 서명] 완료 PDF 합성 + 감사 기록 페이지 → 서명자·참조자·요청자에게 완료 링크 메일 (인증번호, 30일)
```

## 인증

| 방식 | 사용처 | 비고 |
|---|---|---|
| `Authorization: Bearer pst_...` | 외부 연동 | 웹의 **API 토큰** 메뉴에서 발급 |
| 브라우저 세션 | 웹 화면 | 변경 요청에는 `X-XSRF-TOKEN` 헤더 필요 (`XSRF-TOKEN` 쿠키 값) |

| 스코프 | 허용 |
|---|---|
| `templates:read` | 템플릿 목록·상세·PDF 조회 |
| `documents:read` | 문서 목록·상세·PDF·완료 PDF |
| `documents:write` | 서명 요청, 회수 |

템플릿 만들기·수정·삭제와 요청 메일 다시 보내기는 **웹에서만** 됩니다 (지침 1, 19).

## 1. 템플릿 확인

```bash
API=https://pdfsign.158-247-240-171.sslip.io/api/v1
AUTH="Authorization: Bearer pst_..."

curl -s -H "$AUTH" $API/templates            # 번호, 이름, 준비 상태
curl -s -H "$AUTH" $API/templates/T-0001     # 자리(slots), 칸, requestExample(요청 JSON 예시)
```

`requestExample`을 복사해 이메일과 이름만 바꾸면 바로 서명을 요청할 수 있습니다.

## 2. 서명 요청

```bash
curl -s -X POST -H "$AUTH" -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-20261124-001" \
  $API/documents -d '{
  "templateNo": "T-0001",
  "title": "2026년 근로계약서 - 홍길동",
  "message": "확인 후 서명 부탁드립니다.",
  "periodDays": 14,
  "signers": [
    { "slot": "worker", "email": "hong@example.com", "name": "홍길동" },
    { "slot": "ceo",    "email": "ceo@example.com",  "name": "김대표" }
  ],
  "viewers": [
    { "email": "hr@example.com", "name": "인사팀" }
  ]
}'
```

| 항목 | 규칙 |
|---|---|
| `templateNo` | 필수. 요청에 쓸 수 있는(준비된) 템플릿이어야 함 (아니면 409) |
| `signers` | 템플릿의 **모든 자리**를 정확히 한 번씩. 같은 이메일 두 번 불가 |
| `viewers` | 선택, 최대 20명, 순서 없음. 완료 링크만 받음 |
| `title` | 선택. 없으면 템플릿 이름 |
| `periodDays` | 선택, 1~31. 없으면 템플릿 기본값 (기본 14) |
| `Idempotency-Key` 헤더 | 선택. 같은 키로 다시 보내면 새로 만들지 않고 처음 문서를 돌려줌 (네트워크 재시도에 안전) |

발송할 때 템플릿 내용을 문서에 복사합니다. 이후 템플릿을 고쳐도 이미 보낸 문서는 바뀌지 않습니다 (`templateRevision`으로 어느 판으로 보냈는지 확인).

## 3. 조회

```bash
curl -s -H "$AUTH" "$API/documents?status=IN_PROGRESS&q=hong&from=2026-11-01T00:00:00Z"
curl -s -H "$AUTH" $API/documents/{id}
```

- 목록 필터: `status`, `q`(제목·수신자 이메일), `from`/`to`(발송 시각), `hidden`, `page`/`size`(최대 100)
- 상세: 서명자별 순서·상태(`WAITING` → `REQUESTED` → `VIEWED` → `SIGNED` / `REJECTED`), 참조자, 칸과 입력값, 감사 추적, 이어진 문서, `canRecall`

## 4. 회수

```bash
curl -s -X POST -H "$AUTH" $API/documents/{id}/recall
```

진행 중이고 **서명 요청 기간 안**일 때만 됩니다 (아니면 409). 이미 요청을 받은 서명자에게 알림 메일이 갑니다.

## 5. 완료 PDF

```bash
curl -s -H "$AUTH" -o result.pdf $API/documents/{id}/result
```

입력값과 요청자 고정 텍스트를 원본에 그려 넣고, 마지막에 감사 기록 페이지를 붙인 PDF입니다. SHA-256은 상세의 `resultSha256`에 있습니다.

## 문서 상태

| 상태 | 뜻 |
|---|---|
| `IN_PROGRESS` | 진행 중 |
| `COMPLETED` | 모든 서명 완료 |
| `REJECTED` | 서명자가 반려. 후순위 서명자와 관계없이 즉시 종료 (지침 5) |
| `RECALLED` | 요청자가 회수 (지침 6) |
| `EXPIRED` | 서명 요청 기간이 지남 |

## 수신자 쪽 (계정 없음)

수신자 API는 `/sign/api/**`이고 화면(`/sign/{토큰}`)이 씁니다. 외부 연동에서 부를 일은 없습니다.

| 규칙 | 값 |
|---|---|
| 링크 토큰 | 32바이트 난수, DB에는 SHA-256만. 순차에서는 차례가 올 때 발급 |
| 인증번호 | 6자리, 10분 유효, 5번 틀리면 무효, 재발송 60초 간격·1시간 5번 |
| 인증 후 세션 | 2시간, 인증 성공 시 세션 ID 새로 발급 |
| 완료 링크 | 서명자·참조자·요청자, 완료 후 30일 |
| 서명 이미지 | PNG/JPEG, 2MB, 3000px 이하. 서버에서 다시 그려 저장 |

## 좌표

- 단위는 PDF 포인트(1/72인치). A4는 595.28 × 841.89pt.
- 원점은 **페이지 왼쪽 위**. `x`는 오른쪽, `y`는 아래로 커짐.
- 회전된 페이지는 **화면에 보이는 방향** 기준. 칸은 8pt 이상, 페이지 밖으로 나갈 수 없음.

## 오류 형식

[RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457):
```json
{"status":400,"title":"Bad Request","detail":"자리 ceo(대표)의 서명자가 없습니다."}
```
다른 사람의 문서·템플릿은 존재 여부를 숨기기 위해 404로 응답합니다.

## 아직 없는 것 (계획 5단계 이후)
수정 후 재발송, 숨기기, 워터마크, 첨부파일. `docs/REDESIGN-PLAN.md` 참고.
