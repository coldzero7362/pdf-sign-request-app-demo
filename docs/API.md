# API 사용 안내

전체 목록과 요청·응답 형식은 Swagger에서 볼 수 있습니다: `/swagger-ui.html`

## 인증

| 방식 | 사용처 | 비고 |
|---|---|---|
| 브라우저 세션 | 화면 | 변경 요청에는 `X-XSRF-TOKEN` 헤더 필요 (`XSRF-TOKEN` 쿠키 값) |
| `Authorization: Bearer pst_...` | 외부 연동 | 화면의 **API 토큰** 메뉴에서 발급. CSRF 헤더 불필요 |

토큰 스코프:
- `requests:read`: `GET /api/v1/documents/**`, `GET /api/v1/requests/**`
- `requests:write`: 그 밖의 문서·요청 변경

## 기본 흐름

```bash
API=https://pdfsign.158-247-240-171.sslip.io/api/v1
AUTH="Authorization: Bearer pst_..."

# 1) PDF 업로드 (20MB, 50쪽 이하, 암호 없는 PDF)
curl -s -H "$AUTH" -F "file=@contract.pdf" $API/documents
# → {"id":"<documentId>","pageCount":2,"pages":[{"pageNo":1,"width":595.28,"height":841.89,"rotation":0}, ...]}

# 2) 요청 초안 만들기 (expiresAt은 선택, 최대 90일 뒤)
curl -s -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"documentId":"<documentId>","title":"근로계약서 서명","message":"확인 부탁드립니다"}' \
  $API/requests
# → {"id":"<requestId>","status":"DRAFT", ...}

# 3) 수신자와 입력 칸 저장 (초안에서 몇 번이든 통째로 교체)
curl -s -X PUT -H "$AUTH" -H "Content-Type: application/json" $API/requests/<requestId>/layout -d '{
  "recipients": [
    {"key":"a","email":"signer@example.com","name":"홍길동","role":"SIGNER"},
    {"key":"b","email":"hr@example.com","name":"인사팀","role":"VIEWER"}
  ],
  "fields": [
    {"recipientKey":"a","type":"TEXT","page":1,"x":72,"y":120,"width":200,"height":24,"required":true,"label":"이름"},
    {"recipientKey":"a","type":"DATE","page":1,"x":300,"y":120,"width":120,"height":24,"required":true},
    {"recipientKey":"a","type":"SIGNATURE","page":1,"x":72,"y":700,"width":160,"height":60,"required":true}
  ]
}'

# 4) 발송
curl -s -X POST -H "$AUTH" $API/requests/<requestId>/send

# 상태 확인, 감사 기록, 취소
curl -s -H "$AUTH" $API/requests/<requestId>
curl -s -H "$AUTH" $API/requests/<requestId>/audit
curl -s -X POST -H "$AUTH" $API/requests/<requestId>/cancel
```

## 수신자 흐름

수신자는 계정이 없습니다. 메일 링크와 이메일 인증번호(OTP)로 본인을 확인합니다.

```
발송 ─▶ 서명자에게 초대 메일 (링크 https://.../sign/<토큰>)
        │
        ▼
수신자: 링크 열기 → 인증번호 받기 → 6자리 입력 → 문서 열람 (요청 상태 IN_PROGRESS)
        → 칸 채우기 (텍스트, 날짜, 체크, 서명 그리기 또는 이미지) → 제출 / 거절
        │
        ├─ 거절: 요청 DECLINED, 요청자에게 메일
        └─ 모든 서명자 제출: 입력값을 PDF에 합성 + 감사 기록 페이지 → 요청 COMPLETED
                             모든 수신자(열람자 포함)와 요청자에게 완료 메일
```

| 규칙 | 값 |
|---|---|
| 링크 토큰 | 32바이트 난수. DB에는 SHA-256만 저장. 완료되면 새 토큰으로 바꿔 완료본 링크로 보냄 |
| 인증번호 | 6자리, 10분 유효, 5번 틀리면 무효 (다시 받아야 함) |
| 재발송 | 60초 간격, 1시간에 5번까지 |
| 인증 후 세션 | 2시간. 인증 성공 시 세션 ID를 새로 발급 |
| 열람자 | 진행 중에는 들어올 수 없고, 완료되면 완료본만 내려받음 |
| 완료본 접근 | 완료 후 30일 |
| 서명 이미지 | PNG/JPEG, 2MB, 3000px 이하. 서버에서 다시 그려 PNG로 저장 |

수신자 API는 `/sign/api/**`입니다 (Swagger의 "수신자" 묶음). 쿠키 세션을 쓰므로 변경 요청에는 `X-XSRF-TOKEN` 헤더가 필요하고, 첫 POST 전에 `GET /sign/api/csrf`로 쿠키를 받습니다.

요청자는 완료본을 `GET /api/v1/requests/{id}/result`로 내려받습니다. 완료본의 SHA-256은 요청 상세의 `resultSha256`에 있습니다.

### 메일 다시 보내기

```bash
curl -s -X POST -H "$AUTH" $API/requests/<requestId>/recipients/<recipientId>/resend
# → {"email":"signer@example.com","kind":"INVITATION"}   (완료 후에는 "RESULT")
```

| 요청 상태 | 대상 | 보내는 메일 |
|---|---|---|
| 진행 중 (`SENT`, `IN_PROGRESS`) | 아직 제출·거절하지 않은 서명자 | 초대 메일 |
| 완료 (`COMPLETED`, 30일 이내) | 모든 수신자 | 완료본 링크 |

- 보낼 때마다 **링크를 새로 발급하고 이전 링크는 무효**가 됩니다. 메일을 잘못 보냈거나 링크가 샜을 때도 안전하게 다시 보낼 수 있습니다.
- 수신자마다 1분 간격, 24시간에 5번까지 보낼 수 있습니다. 넘으면 429를 돌려줍니다. 재발송은 감사 기록(`MAIL_RESENT`)에 남습니다.
- 조건에 맞지 않으면 409를 돌려줍니다 (열람자에게 초대 메일, 이미 제출한 서명자 등).

기한(`expiresAt`)이 지난 요청은 접근할 때와 10분마다 `EXPIRED`로 바뀝니다.

## 좌표

- 단위는 **PDF 포인트**(1/72인치)입니다. A4는 595.28 × 841.89pt입니다.
- 원점은 **페이지 왼쪽 위**입니다. `x`는 오른쪽으로, `y`는 아래로 커집니다.
- 회전된 페이지(`rotation` 90, 270)는 **화면에 보이는 방향** 기준입니다. 업로드 응답의 `pages[].width`, `height`도 이미 회전을 반영한 값입니다.
- 칸은 8pt 이상이어야 하고, 페이지 밖으로 나갈 수 없습니다.

## 입력 칸 종류

| type | 수신자가 넣는 값 |
|---|---|
| `TEXT` | 텍스트 |
| `DATE` | 날짜 |
| `CHECKBOX` | 체크 여부 |
| `SIGNATURE` | 손글씨 서명 또는 이미지(도장 등) |

`VIEWER` 수신자에게는 칸을 줄 수 없습니다. 발송하려면 모든 `SIGNER`에게 칸이 하나 이상 있어야 합니다.

## 요청 상태

```
DRAFT → SENT → IN_PROGRESS → COMPLETED
          ├→ DECLINED  (수신자 거절)
          ├→ CANCELED  (요청자 취소)
          └→ EXPIRED   (기한 초과)
```

- 수정(`PUT /requests/{id}`, `/layout`)과 삭제는 `DRAFT`에서만 됩니다. 그 외 상태에서는 409를 돌려줍니다.
- 취소는 `SENT`와 `IN_PROGRESS`에서만 됩니다.
- 발송한 요청은 감사 기록을 남기기 위해 삭제할 수 없습니다.

## 오류 형식

오류는 [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) 형식으로 돌려줍니다.

```json
{"status":400,"title":"Bad Request","detail":"1쪽 칸이 페이지 밖으로 나갔습니다."}
```

다른 사람의 문서나 요청에 접근하면, 존재 여부를 숨기기 위해 403이 아닌 404를 돌려줍니다.
