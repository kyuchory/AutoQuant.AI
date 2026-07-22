# 실시간 AI 모의투자 시스템 — API 명세서 (v1)

> 이 문서는 테이블 설계 문서 v3, 워크플로우 설계 문서 v2, AI Agent 시스템 프롬프트 v2, 로그인 방식(JWT Access+Refresh) 논의 내용을 기반으로 작성되었습니다.

---

## 0. 문서 작성 시 채택한 가정 (Assumptions)

아래 항목은 이전 대화에서 명시적으로 확정되지 않아, 업계 표준 패턴을 기본값으로 채택했습니다. 실제 구현 전 팀 컨벤션과 다르면 이 부분만 수정하면 됩니다.

| 항목 | 채택한 기본값 |
|---|---|
| 공통 응답 포맷 | `{ success, code, message, data }` |
| 목록 페이징 | Offset 기반 (`page`, `size` 쿼리 파라미터) |
| WebSocket 메시지 포맷 | `{ type, payload }` 구조의 JSON 이벤트 |
| 에러 코드 체계 | `E{HTTP상태코드}{일련번호}` 형식 (예: E4001) |
| Refresh Token 재발급 정책 | Rotation 방식 (재발급 시마다 새 Refresh Token 발급) |
| 인증 헤더 | `Authorization: Bearer {accessToken}` |

---

## 1. 공통 규칙

### 1.1 Base URL
```
/api/v1
```

### 1.2 공통 응답 포맷

**성공 응답**
```json
{
  "success": true,
  "code": "S0000",
  "message": "OK",
  "data": { }
}
```

**실패 응답**
```json
{
  "success": false,
  "code": "E4041",
  "message": "요청한 리소스를 찾을 수 없습니다.",
  "data": null
}
```

### 1.3 인증 헤더
```
Authorization: Bearer {accessToken}
```
- `POST /auth/login`, `POST /auth/refresh` 를 제외한 모든 API는 이 헤더가 필수.
- WebSocket 연결 시에도 최초 핸드셰이크에 동일 Access Token을 쿼리 파라미터(`?token=`) 또는 `Sec-WebSocket-Protocol` 헤더로 전달.

### 1.4 공통 에러 코드

| 코드 | HTTP Status | 의미 |
|---|---|---|
| E4001 | 400 | 요청 값 검증 실패 (예: conditionLogic 없이 두 조건 동시 입력 등) |
| E4010 | 401 | 인증 토큰 없음/만료 |
| E4011 | 401 | Refresh Token 만료 또는 불일치 |
| E4030 | 403 | 권한 없음 (본인 소유가 아닌 리소스 접근) |
| E4041 | 404 | 리소스 없음 |
| E4090 | 409 | 중복 리소스 (`uk_holdings_user_stock`, `uk_users_email` 등 제약 위반) |
| E5000 | 500 | 서버 내부 오류 |
| E5030 | 503 | 외부 API(KIS/네이버/OpenAI) 장애 |

### 1.5 페이징 공통 쿼리 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| page | int | 0 | 0부터 시작하는 페이지 번호 |
| size | int | 20 | 페이지당 항목 수 |

**페이징 응답 래퍼**
```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

---

## 2. 인증 (Authentication)

### 2.1 `POST /api/v1/auth/login`

OAuth Authorization Code를 받아 로그인 처리 및 JWT 발급. 신규 유저는 자동 회원가입.

**Request Body**
```json
{
  "provider": "GOOGLE",
  "code": "4/0AY0e-g7..."
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| provider | string | Y | `GOOGLE`, `KAKAO` |
| code | string | Y | OAuth Authorization Code |

**Response 200**
```json
{
  "success": true,
  "code": "S0000",
  "message": "OK",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "accessTokenExpiresIn": 3600,
    "isNewUser": false,
    "user": {
      "userId": 1,
      "nickname": "규철",
      "email": "user@example.com"
    }
  }
}
```
- Refresh Token은 응답 바디에 포함하지 않고 `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Max-Age=1209600` 로 전달.

**에러**
- `E4001` : 지원하지 않는 provider
- `E5030` : OAuth 서버 통신 실패

---

### 2.2 `POST /api/v1/auth/refresh`

Refresh Token(쿠키)으로 Access Token 재발급. Rotation 정책에 따라 Refresh Token도 함께 재발급.

**Request**: Body 없음. 쿠키의 `refreshToken` 자동 사용.

**Response 200**
```json
{
  "success": true,
  "code": "S0000",
  "message": "OK",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "accessTokenExpiresIn": 3600
  }
}
```
- 새 Refresh Token은 다시 `Set-Cookie`로 갱신.

**에러**
- `E4011` : Redis의 `refresh:{userId}` 값과 불일치 또는 만료 → 프론트는 재로그인 화면으로 이동

---

### 2.3 `POST /api/v1/auth/logout`

**Request**: 헤더의 Access Token 기준으로 처리.

**동작**
1. Access Token의 `jti`를 Redis `blacklist:{jti}` 로 등록 (TTL = 토큰 잔여 만료시간)
2. Redis `refresh:{userId}` 삭제
3. `Set-Cookie: refreshToken=; Max-Age=0` 로 쿠키 제거 지시

**Response 200**
```json
{ "success": true, "code": "S0000", "message": "로그아웃되었습니다.", "data": null }
```

---

## 3. 자산 (Assets)

### 3.1 `GET /api/v1/assets`

유저의 지갑(Wallet) 잔고 + 보유 종목(Holdings) 목록 + 실시간 평가금액을 조합해 반환.

**Response 200**
```json
{
  "success": true,
  "code": "S0000",
  "message": "OK",
  "data": {
    "walletBalance": 8500000.0000,
    "totalEvaluationAmount": 12300000.0000,
    "totalProfitLossAmount": 300000.0000,
    "totalProfitLossRate": 2.5,
    "holdings": [
      {
        "stockCode": "005930",
        "stockName": "삼성전자",
        "quantity": 10,
        "averagePrice": 78000.0000,
        "currentPrice": 79500.0000,
        "evaluationAmount": 795000.0000,
        "profitLossRate": 1.92
      }
    ]
  }
}
```
- `currentPrice`는 Redis `price:{stockCode}:current` 조회 값.

---

### 3.2 `POST /api/v1/assets/orders`

수동 매매 주문.

**Request Body**
```json
{
  "stockCode": "005930",
  "orderType": "BUY",
  "quantity": 5
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| stockCode | string | Y | 종목 코드 |
| orderType | string | Y | `BUY`, `SELL` |
| quantity | int | Y | 1 이상 |

**Response 200** (`trading_histories` 레코드 반환)
```json
{
  "success": true,
  "code": "S0000",
  "message": "OK",
  "data": {
    "historyId": 101,
    "stockCode": "005930",
    "orderType": "BUY",
    "status": "FILLED",
    "executionPrice": 79500.0000,
    "executionQuantity": 5,
    "totalAmount": 397500.0000,
    "executedAt": "2026-07-12T15:32:10"
  }
}
```
- `status`가 `PENDING`으로 즉시 반환될 수도 있으며, 이 경우 WebSocket `ORDER_FILLED`/`ORDER_FAILED` 이벤트로 최종 결과를 push.

**에러**
- `E4001` : 잔고 부족(SELL 시 보유 수량 부족, BUY 시 예수금 부족)
- `E4041` : 존재하지 않는 종목 코드

---

### 3.3 `GET /api/v1/assets/histories`

매매 체결 이력 조회 (페이징).

**Query Parameters**: `page`, `size`, `stockCode`(optional), `status`(optional: PENDING/FILLED/FAILED)

**Response 200**: §1.5 페이징 래퍼 + `trading_histories` 필드 목록.

---

## 4. 자동 매매 조건 (Conditions)

### 4.1 `POST /api/v1/conditions`

**Request Body**
```json
{
  "stockCode": "005930",
  "targetPrice": 85000.0000,
  "priceConditionType": "ABOVE",
  "targetAiScore": 80,
  "aiConditionType": "ABOVE",
  "conditionLogic": "AND",
  "orderType": "BUY",
  "orderQuantity": 3
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| stockCode | string | Y | 종목 코드 |
| targetPrice | decimal | N | `priceConditionType`과 세트 |
| priceConditionType | string | N | `ABOVE`, `BELOW` |
| targetAiScore | int | N | `aiConditionType`과 세트, 0~100 |
| aiConditionType | string | N | `ABOVE`, `BELOW` |
| conditionLogic | string | Y | `AND`, `OR` (조건 1개만 설정 시 무시됨) |
| orderType | string | Y | `BUY`, `SELL` |
| orderQuantity | int | Y | 1 이상 |

**검증 규칙 (DTO 레벨, `chk_conditions_type_pair`와 동일)**
- (targetPrice ≠ null AND priceConditionType ≠ null) OR (targetAiScore ≠ null AND aiConditionType ≠ null) 이어야 함
- 위반 시 `E4001` + 메시지 "가격 조건 또는 AI 점수 조건을 값-타입 쌍으로 입력해야 합니다."

**Response 201**
```json
{
  "success": true,
  "code": "S0000",
  "message": "OK",
  "data": { "conditionId": 55, "isActive": true }
}
```

---

### 4.2 `GET /api/v1/conditions`

내가 설정한 조건 목록 (페이징).

**Query Parameters**: `page`, `size`, `isActive`(optional)

**Response 200**: §1.5 페이징 래퍼 + `trading_conditions` 필드 목록.

---

### 4.3 `DELETE /api/v1/conditions/{conditionId}`

**Response 200**
```json
{ "success": true, "code": "S0000", "message": "삭제되었습니다.", "data": null }
```

**에러**
- `E4030` : 본인 소유가 아닌 조건 삭제 시도
- `E4041` : 존재하지 않는 conditionId

---

## 5. AI 투자 리포트 (Reports)

### 5.1 `GET /api/v1/reports/stocks/{stockCode}`

Redis 캐시 우선 조회. 캐시 미스 시 `ai_investment_reports`의 최신 레코드 반환 (동기 조회, 신규 생성은 하지 않음).

**Response 200**
```json
{
  "success": true,
  "code": "S0000",
  "message": "OK",
  "data": {
    "reportId": 301,
    "stockCode": "005930",
    "reportContent": "## 삼성전자 투자 분석\n...",
    "createdAt": "2026-07-12T09:00:00",
    "cacheHit": true
  }
}
```

**에러**
- `E4041` : 아직 생성된 리포트가 없는 경우 → 프론트는 "새로고침" 버튼 유도

---

### 5.2 `POST /api/v1/reports/stocks/{stockCode}/refresh`

Redis 캐시를 우회하고 RabbitMQ에 새 리포트 생성 티켓 발행 (비동기).

**Response 202**
```json
{
  "success": true,
  "code": "S0000",
  "message": "분석 요청이 접수되었습니다.",
  "data": { "requestId": "a1b2c3", "status": "ACCEPTED" }
}
```
- 최종 결과는 WebSocket `REPORT_READY` 이벤트로 push되거나, 클라이언트가 §5.1을 재조회.

**에러**
- `E4290` (429) : 동일 종목에 대해 짧은 시간 내 중복 새로고침 요청 (Rate limit — 예: 종목당 1분 1회)

---

## 6. 실시간 알림 (WebSocket)

### 6.1 연결

```
wss://{host}/ws?token={accessToken}
```
- 연결 시점에 Access Token을 검증해 `userId`를 세션에 바인딩.

### 6.2 서버 → 클라이언트 이벤트

**공통 포맷**
```json
{ "type": "EVENT_TYPE", "payload": { } }
```

| type | 발생 시점 | payload 예시 |
|---|---|---|
| `PRICE_ALERT` | 가격 조건 충족 | `{ conditionId, stockCode, currentPrice }` |
| `AI_SCORE_ALERT` | AI 감성 점수 조건 충족 | `{ conditionId, stockCode, aiScore }` |
| `ORDER_FILLED` | 자동/수동 주문 체결 완료 | `{ historyId, stockCode, executionPrice, executionQuantity }` |
| `ORDER_FAILED` | 자동 주문 체결 실패 | `{ historyId, stockCode, failureReason }` |
| `REPORT_READY` | RAG 리포트 생성 완료 | `{ stockCode, reportId }` |

---

## 7. 뉴스 (News) — v1.1 신규

### 7.1 `GET /api/v1/news/ticker`

is_monitored = TRUE인 모든 종목의 최신 뉴스 1건씩 반환합니다.

**Response 200**
```json
{
  "success": true,
  "code": "S0000",
  "message": "OK",
  "data": [
    {
      "stockCode": "005930",
      "stockName": "삼성전자",
      "title": "삼성전자, 고대역폭 ...",
      "newsUrl": "https://n.news.naver.com/...",
      "sentiment": "GOOD",
      "aiScore": 88,
      "publishedAt": "2026-07-22T15:30:00"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| stockCode | string | 종목 코드 |
| stockName | string | 종목명 |
| title | string | 뉴스 제목 |
| newsUrl | string | 뉴스 원문 링크 |
| sentiment | string | AI 감성 결과 (GOOD/BAD/NEUTRAL) |
| aiScore | int | AI 호재 점수 (0~100) |
| publishedAt | string | 뉴스 원 발행 시간 (ISO 8601) |

**에러**
- `E4010` : 인증 토큰 없음/만료

---

## 8. 엔드포인트 요약표

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | /api/v1/auth/login | N | OAuth 로그인 + JWT 발급 |
| POST | /api/v1/auth/refresh | N (쿠키) | Access Token 재발급 |
| POST | /api/v1/auth/logout | Y | 로그아웃 |
| GET | /api/v1/assets | Y | 지갑+보유종목 종합 조회 |
| POST | /api/v1/assets/orders | Y | 수동 매매 주문 |
| GET | /api/v1/assets/histories | Y | 매매 이력 조회 |
| POST | /api/v1/conditions | Y | 자동 매매 조건 등록 |
| GET | /api/v1/conditions | Y | 조건 목록 조회 |
| DELETE | /api/v1/conditions/{conditionId} | Y | 조건 삭제 |
| GET | /api/v1/reports/stocks/{stockCode} | Y | 최신 리포트 조회 |
| POST | /api/v1/reports/stocks/{stockCode}/refresh | Y | 리포트 재생성 요청 |
| GET | /api/v1/news/ticker | Y | 대시보드 뉴스 티커 (종목별 최신) |
| WS | /ws | Y (쿼리 토큰) | 실시간 알림 구독 |
