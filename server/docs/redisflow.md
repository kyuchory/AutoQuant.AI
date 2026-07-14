# 실시간 AI 모의투자 시스템 — Redis 데이터 설계 문서 (v1)

> 이 문서는 워크플로우 v2, 테이블 설계 v3, API 명세서 v1, 로그인(JWT) 설계, KIS 인증 흐름 문서, Redis 키 논의를 종합해 Redis에 올라가는 모든 데이터의 Key/Value/TTL을 고정합니다. 이후 구현 시 이 문서의 Key 패턴을 임의로 변경하지 않는 것을 원칙으로 합니다.

---

## 0. Key 네이밍 컨벤션

시스템 프롬프트 §4.1 규칙(`Domain:Identifier:DataType`)을 전체 Redis 키에 일관 적용한다.

```
{domain}:{sub-domain?}:{identifier}:{data-type}
```

- 최상위 domain은 소문자 고정 단어: `price`, `report`, `auth`, `kis`, `ws`, `rate`
- 여러 단어로 구성된 식별자는 `:`로 계층을 나누며 `-`나 `_`는 쓰지 않는다.

---

## 1. 전체 Key 목록 (고정본)

| # | Domain | Key 패턴 | Value | 자료구조 | TTL | Writer | Reader | 상태 |
|---|---|---|---|---|---|---|---|---|
| 1 | 시세 | `price:{stockCode}:current` | 현재 체결가 (숫자, 문자열로 저장) | String | 없음 (계속 SET) | `KisWebsocketClient` | `AssetSummaryService`, 조건 매칭 워커 | 확정 |
| 2 | AI 리포트 | `report:{stockCode}:text` | 리포트 본문 (Markdown 문자열) | String | **12시간** | RabbitMQ `ReportWorker` | `GET /reports/stocks/{code}` | 확정 |
| 3 | 인증 | `auth:{userId}:refreshToken` | Refresh Token 값 | String | **14일** | `AuthService` (로그인/refresh 시) | `POST /auth/refresh` | 확정 |
| 4 | 인증 | `auth:{jti}:blacklist` | `"1"` (존재 여부만 체크) | String | Access Token 잔여 만료시간과 동일 | `AuthService` (로그아웃 시) | JWT 인증 필터 (매 요청) | 확정 |
| 5 | KIS 외부인증 | `kis:auth:accessToken` | KIS Access Token 값 | String | **5시간 50분** | 앱 기동 시 + 스케줄러(5.5시간 주기) | `KisWebsocketClient`, 주문 실행 클라이언트 | 확정 |
| 6 | KIS 외부인증 | `kis:auth:approvalKey` | 웹소켓 접속키(approval_key) | String | **5시간 50분** | 앱 기동 시 + 재연결 시 | `KisWebsocketClient` | 확정 |
| 7 | 동시성 락 | `rate:order:lock:{userId}:{stockCode}` | `"locked"` | String (NX 옵션으로 SET) | **4초** | 조건 매칭 워커 (주문 시도 직전) | 동일 워커 (락 존재 여부 확인) | 확정 |
| 8 | 전역 레이트리미터 | `rate:kis:global:orderCount` | 슬라이딩 윈도우 카운터 | String (INCR) 또는 Sorted Set | **1초** (윈도우 주기에 맞춰 자동 만료) | 주문 실행 클라이언트 (호출 직전 INCR) | 동일 클라이언트 (한도 초과 시 큐잉/대기) | 확정 |
| 9 | 웹소켓 라우팅 | `ws:session:{userId}` | 접속 여부/서버 인스턴스 ID | String | 30분 (핑퐁 연장) + onClose 시 즉시 DEL | WebSocket 연결/해제 핸들러 | 조건 매칭 워커 (알림 전송 대상 판별) | **보류 — 확장 포인트로만 기록, 지금은 미구현** |

---

## 2. 항목별 상세 규격

### 2.1 `price:{stockCode}:current` — 실시간 시세

```
Key   : price:005930:current
Value : "79500.0000"
자료구조: String
TTL   : 없음 (KIS Websocket이 체결가를 수신할 때마다 SET으로 덮어씀)
```
- 종목은 `stocks.is_monitored = TRUE`인 10종목으로 키 개수가 고정됨(`price:005930:current` ~ `price:XXXXXX:current`).
- 값은 소수점 4자리까지 문자열로 저장(테이블의 `DECIMAL(18,4)`와 정밀도 일치).
- 장 마감 후에도 마지막 체결가가 유지되는 것이 의도된 동작.

---

### 2.2 `report:{stockCode}:text` — AI 투자 리포트 캐시

```
Key   : report:005930:text
Value : "## 삼성전자 투자 분석\n..." (Markdown 문자열)
자료구조: String
TTL   : 43200초 (12시간)
```
- **캐시 무효화(우선순위 1순위)**: 뉴스 수집 스케줄러가 해당 종목의 신규 뉴스를 `news_sentiments`에 INSERT하는 시점에 이 키를 즉시 `DEL`한다. 12시간 TTL은 무효화 로직이 어떤 이유로든 발동하지 않았을 때의 보조 안전장치일 뿐이다.
- 캐시 미스 시 `ai_investment_reports` 최신 레코드를 대신 반환(§API 명세서 5.1)하며, 이 경우 자동으로 Redis에 다시 채워 넣지 않는다(새 리포트 생성은 `/refresh` 트리거로만 발생).

---

### 2.3 `auth:{userId}:refreshToken` — Refresh Token

```
Key   : auth:1:refreshToken
Value : "eyJhbGciOi..." (Refresh Token 원문 또는 해시값)
자료구조: String
TTL   : 1209600초 (14일)
```
- 로그인 성공 또는 `/auth/refresh` 호출(Rotation) 시마다 값과 TTL을 갱신(SET으로 덮어쓰기, TTL 재설정).
- `/auth/refresh` 요청 시 쿠키의 Refresh Token과 이 값이 정확히 일치해야 재발급 진행. 불일치 시 탈취 의심으로 간주하고 즉시 키 삭제 + 재로그인 요구(선택적 보안 강화 옵션).
- 유저당 정확히 1개만 유지(다중 기기 동시 로그인을 지원하려면 `auth:{userId}:refreshToken:{deviceId}`로 키를 확장해야 하며, 이는 현재 스코프 밖).

---

### 2.4 `auth:{jti}:blacklist` — Access Token 무효화 블랙리스트

```
Key   : auth:a1b2c3d4-e5f6:blacklist
Value : "1"
자료구조: String
TTL   : 해당 Access Token의 남은 유효시간(초) — 로그아웃 시점에 토큰 만료시각까지 역산해서 설정
```
- 존재 여부만 검사하면 되므로 값은 의미 없는 placeholder(`"1"`).
- JWT 인증 필터는 매 요청마다 토큰의 `jti` claim으로 이 키의 존재 여부를 먼저 확인하고, 존재하면 즉시 401(E4010) 반환.
- TTL을 Access Token 잔여 만료시간과 정확히 맞춰야 하는 이유: 그보다 길게 잡으면 불필요하게 Redis 메모리를 점유하고, 짧게 잡으면 토큰이 아직 유효한데 블랙리스트가 먼저 풀려 재사용이 가능해지는 보안 허점이 생김.

---

### 2.5 `kis:auth:accessToken` / `kis:auth:approvalKey` — KIS 외부 인증

```
Key   : kis:auth:accessToken
Value : "eyJhbGciOi..." (KIS Access Token)
자료구조: String
TTL   : 21000초 (5시간 50분)

Key   : kis:auth:approvalKey
Value : "xxxxx-xxxxx-xxxxx" (approval_key)
자료구조: String
TTL   : 21000초 (5시간 50분)
```
- KIS 공식 유효기간은 24시간이지만, KIS 기술문서 권고에 따라 **6시간 주기 선제 갱신**을 원칙으로 한다. Redis TTL을 6시간보다 짧은 5시간 50분으로 잡아, 스케줄러가 TTL 만료 전에 갱신하도록 여유를 둔다.
- 이 두 키는 유저별이 아니라 **애플리케이션 전역에 단 하나씩만 존재**한다(Client Credentials 방식이므로 사용자 단위 토큰이 아님).
- `KisWebsocketClient`는 재연결 시 이 키들이 유효한지 먼저 확인하고, TTL이 임박했거나 만료되었다면 재발급 후 재연결을 시도한다.
- 갱신 주체는 `KisAuthClient`이며, 앱 기동 시 최초 발급 + 이후 스케줄러(예: 5시간 30분 주기)로 선제 갱신한다.

---

### 2.6 `rate:order:lock:{userId}:{stockCode}` — 자동매매 동시성 락

```
Key   : rate:order:lock:1:005930
Value : "locked"
자료구조: String (SET ... NX EX 4)
TTL   : 4초
```
- 조건 매칭 워커가 주문을 시도하기 직전 `SET rate:order:lock:{userId}:{stockCode} locked NX EX 4`로 락 획득을 시도한다.
- 락 획득 실패(이미 존재) → 해당 주문 시도는 스킵(동시 중복 주문 방지).
- **이 락은 무한매수 방지의 2차 방어선이다.** 1차 방어선은 `trading_conditions.is_active`를 체결 성공 시 `FALSE`로 전환하는 로직이며, 이 락은 "같은 순간에 여러 워커/스레드가 동시에 같은 조건을 처리하는 경우"만 방지한다. `is_active` 전환 로직이 실패하거나 지연되는 경우까지 이 락 하나로 막을 수는 없으므로, 두 로직을 항상 함께 구현한다.

---

### 2.7 `rate:kis:global:orderCount` — KIS 전역 호출 레이트리미터

```
Key   : rate:kis:global:orderCount:{epochSecond}
Value : 카운터 (INCR로 증가)
자료구조: String (INCR) — 초 단위로 키 자체가 바뀌는 슬라이딩 방식
TTL   : 2초 (해당 초가 지나면 자동 소멸)
```
- 주문 실행 클라이언트가 KIS REST 주문 API를 호출하기 직전 현재 초(`epochSecond`) 기준 키를 `INCR`.
- 반환된 카운트가 KIS의 초당 허용 호출 수(예: 실제 한도는 KIS 공식 문서 확인 후 상수화)를 넘으면, 해당 요청은 다음 초까지 대기열(RabbitMQ 재발행 또는 짧은 딜레이 후 재시도)로 보낸다.
- 이 카운터는 §2.6의 유저별 락과 별개로, "유저 여러 명이 동시에 각자 다른 종목 주문을 넣어 KIS 서버 전체 호출 한도를 넘기는 상황"을 방지하기 위한 것이다.
- 실제 KIS 호출 한도 수치는 모의투자 계좌 기준으로 별도 확인이 필요하며, 확인되는 대로 이 문서의 임계값을 상수로 명시한다 (현재는 자리만 확보).

---

### 2.8 `ws:session:{userId}` — 웹소켓 라우팅 (보류, 확장 포인트)

```
Key   : ws:session:1
Value : 서버 인스턴스 ID 또는 "true"
자료구조: String
TTL   : 1800초(30분) + 클라이언트 핑퐁으로 연장, onClose 시 즉시 DEL
```
- **현재 스코프에서는 미구현.** 서버가 1대로 운영되는 동안은 로컬 메모리 세션 맵으로 충분하다.
- 서버를 2대 이상으로 스케일아웃하는 시점에, 아래 중 하나를 선택해 도입한다.
  - **권장안**: Redis Pub/Sub 전역 채널 브로드캐스트 방식 — 워커는 이벤트를 전역 채널에 발행하고, 각 서버 인스턴스는 이를 구독하다가 자기 로컬 세션 맵에 해당 유저가 있으면 전달. 이 키 자체가 필요 없어짐.
  - **대안**: 이 표의 키로 유저→인스턴스 라우팅 테이블을 명시적으로 관리. 인스턴스별 관리·정리 로직이 추가로 필요해 구현 비용이 더 크다.

---

## 3. 캐시 무효화 / 정리 규칙 요약

| Key | 무효화 트리거 | 방식 |
|---|---|---|
| `report:{stockCode}:text` | 해당 종목 신규 뉴스 INSERT | 즉시 DEL |
| `auth:{userId}:refreshToken` | 로그아웃, Refresh Token Rotation, 토큰 탈취 의심 | 즉시 DEL 또는 덮어쓰기 |
| `auth:{jti}:blacklist` | 자연 만료(TTL) | 자동 소멸 |
| `kis:auth:*` | 스케줄러 선제 갱신 | 덮어쓰기 (기존 값 자동 대체) |
| `rate:order:lock:*` | 자연 만료(TTL 4초) | 자동 소멸, 별도 DEL 불필요 |
| `rate:kis:global:orderCount:*` | 자연 만료(TTL 2초, 초 단위 키 자체 회전) | 자동 소멸 |
| `ws:session:*` (도입 시) | WebSocket `onClose` | 즉시 DEL, TTL은 보조 안전장치 |

---

## 4. 구현 시 체크리스트

- [ ] 모든 Key는 본 문서의 패턴을 그대로 사용하며, 팀/Agent가 임의로 접두사나 구분자를 바꾸지 않는다.
- [ ] `price`, `kis:auth` 키는 애플리케이션 전역 단일 값이며 `userId`를 포함하지 않는다 (혼동 방지).
- [ ] `report` 캐시 무효화 로직은 뉴스 저장 트랜잭션과 같은 흐름(또는 직후 이벤트)에서 반드시 함께 처리한다.
- [ ] 자동매매 무한 반복 방지는 `is_active`(1차) + `rate:order:lock`(2차) 두 겹으로 구현하며, 락만으로 충분하다고 가정하지 않는다.
- [ ] `kis:auth:*` TTL(5시간50분)은 KIS 권고 갱신주기(6시간)보다 반드시 짧게 유지한다.
- [ ] `ws:session:*`은 지금 구현하지 않으며, 스케일아웃 시점에 Pub/Sub 브로드캐스트 방식을 우선 검토한다.