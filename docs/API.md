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
