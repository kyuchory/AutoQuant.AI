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
| 1 | 시세 | `price:{stockCode}:current` | 현재 체결가 (숫자, 문자열로 저장) | String | [v10 정정] 3분 (`RedisKeys.PRICE_CURRENT_TTL`) | `KisWebsocketClient` (실시간 SET), `RedisPriceClient` (v9: 캐시 미스 시 KIS REST(FHKST01010100) 폴백 SET) | `AssetSummaryService`, 조건 매칭 워커 | v10 갱신 |
| 2 | AI 리포트 | `report:{stockCode}:text` | 리포트 본문 (JSON 문자열) | String | **12시간** | RabbitMQ `ReportWorker` | `GET /reports/stocks/{code}` | 확정 |
| 3 | 인증 | `auth:{userId}:refreshToken` | Refresh Token 값 | String | **14일** | `AuthService` (로그인/refresh 시) | `POST /auth/refresh` | 확정 |
| 4 | 인증 | `auth:{jti}:blacklist` | `"1"` (존재 여부만 체크) | String | Access Token 잔여 만료시간과 동일 | `AuthService` (로그아웃 시) | JWT 인증 필터 (매 요청) | 확정 |
| 5 | KIS 외부인증 | `kis:auth:accessToken` | KIS Access Token 값 | String | **5시간 50분** | 앱 기동 시 + 스케줄러(5.5시간 주기) | `KisWebsocketClient`, 주문 실행 클라이언트 | 확정 |
| 6 | KIS 외부인증 | `kis:auth:approvalKey` | 웹소켓 접속키(approval_key) | String | **5시간 50분** | 앱 기동 시 + 재연결 시 | `KisWebsocketClient` | 확정 |
| 7 | 동시성 락 | `rate:order:lock:{userId}:{stockCode}` | `"locked"` | String (NX 옵션으로 SET) | **4초** | 조건 매칭 워커 (주문 시도 직전) | 동일 워커 (락 존재 여부 확인) | 확정 |
| 8 | 전역 레이트리미터 | ~~`rate:kis:global:orderCount`~~ (v9: 미사용 — §2.7 참고) | - | - | - | - | - | **v9: 인메모리 `KisRateLimiter`로 대체, Redis 키 미사용** |
| 9 | 웹소켓 라우팅 | `ws:session:{userId}` | 접속 여부/서버 인스턴스 ID | String | 30분 (핑퐁 연장) + onClose 시 즉시 DEL | WebSocket 연결/해제 핸들러 | 조건 매칭 워커 (알림 전송 대상 판별) | **보류 — 확장 포인트로만 기록, 지금은 미구현** |
| 10 | 차트 캐시 | `chart:{stockCode}:daily:{period}` | ChartResponse JSON | String | D:10분, W:1시간, M:6시간, Y:24시간 | `ChartService` (Cache Miss 시 KIS 호출 후 저장) | `GET /charts/{stockCode}/daily?period=` | v5 신규 |
| 11 | 차트 캐시(분봉) | `chart:{stockCode}:minute` | ChartResponse JSON | String | 다음날 09:00까지 | `ChartService` (장 마감 후 저장) | `GET /charts/{stockCode}/minute` | v5 신규 |
| 12 | 시세 등락률 | `price:{stockCode}:changeRate` | 전일대비 등락률 (예: "1.25") | String | 24시간 | `KisWebsocketClient` (실시간 SET), `RedisPriceClient` (v9: KIS REST API fallback SET — 기존 `StockService`에 있던 폴백 로직을 `RedisPriceClient.getCurrentPrice()` 내부로 이동해 `price:current`/`changeRate`를 한 번의 KIS 호출로 함께 갱신) | `StockService` (GET /stocks 호출 시), `RedisPriceClient.getChangeRate()` | v9 갱신 |
| 13 | AI 리포트 새로고침 레이트리밋 | `rate:report:refresh:{stockCode}` | `"1"` (존재 여부만 체크) | String (SET NX EX 30) | **30초** | `ReportController.refreshReport()` (요청 접수 직전 SET NX) | 동일 컨트롤러 (락 존재 여부 확인) | v10 신규 (api.md §5.4 E4290) |
| 14 | 자동매매 체결 실패 재시도 카운터 | `rate:condition:retryCount:{conditionId}` | 실패 누적 횟수 | String (INCR) | **5분** | `ConditionMatchingEngine.execute()` | 동일 엔진 (최대 재시도 판단) | v10 신규 |
| 15 | 자동매매 체결 실패 백오프 | `rate:condition:backoff:{conditionId}` | `"1"` | String (SET NX EX 10) | **10초** | `ConditionMatchingEngine.execute()` | 동일 엔진 (백오프 중 스킵) | v10 신규 |

---

## 2. 항목별 상세 규격

### 2.1 `price:{stockCode}:current` — 실시간 시세

```
Key   : price:005930:current
Value : "79500.0000"
자료구조: String
TTL   : [v10 정정] 3분 (`RedisKeys.PRICE_CURRENT_TTL`) — 이전 버전 문서는 "TTL 없음"으로 고정했으나
        실제 구현은 구독 끊김 시 값이 영구히 stale해지는 걸 막기 위해 3분 TTL을 둔다.
```
- 종목은 `stocks.is_monitored = TRUE`인 10종목으로 키 개수가 고정됨(`price:005930:current` ~ `price:XXXXXX:current`).
- 값은 소수점 4자리까지 문자열로 저장(테이블의 `DECIMAL(18,4)`와 정밀도 일치).
- **[v10 정정]** "장 마감 후에도 마지막 체결가가 유지되는 것"은 여전히 사용자에게 보이는 동작이지만, 그 방식이 바뀌었다 — 키 자체는 3분 뒤 만료되고, 다음 조회 시 아래 Writer 2(REST 폴백)가 즉시 재조회해 다시 채운다. 즉 "무제한 TTL로 값이 안 지워짐"이 아니라 "TTL은 있지만 캐시미스 시 자동 복구됨"으로 동일한 사용자 경험을 만든다.
- **[v9] Writer 2 추가**: `RedisPriceClient.getCurrentPrice()`가 이 키를 GET해서 값이 없으면(서버 재시작 직후, 장마감 후 웹소켓이 아직 값을 채우지 못한 경우, 또는 위 3분 TTL 만료 등) `KisChartClient.getCurrentQuote()`(REST FHKST01010100)로 마지막 체결가를 조회해 이 키에 SET하고 반환한다. 이 덕분에 `StockService`/`ChartService`/`AssetSummaryService` 세 호출부 모두 웹소켓 미수신 상황에서도 0 대신 실제 마지막 체결가를 얻는다.

---

### 2.1-2 `price:{stockCode}:changeRate` — 전일대비 등락률 (v6 신규)

```
Key   : price:005930:changeRate
Value : "1.25" 또는 "-2.84"
자료구조: String
TTL   : 86400초 (24시간)
```
- **Writer 1**: `KisWebsocketClient` — 실시간 H0STCNT0 수신 시 fields[5](PRDY_CTRT) 값을 SET.
- **Writer 2 [v9]**: `RedisPriceClient.getCurrentPrice()` — `price:{stockCode}:current` 캐시 미스 시 KIS REST API(FHKST01010100)를 호출하면서, 응답에 포함된 `prdy_ctrt` 값도 함께 이 키에 SET (fallback). 기존에는 `StockService`가 이 SET을 직접 수행했으나, 현재가/등락률 폴백을 한 번의 KIS 호출로 묶기 위해 `RedisPriceClient` 내부로 이동했다.
- **Reader**: `StockService.getMonitoredStocksWithPrice()` — GET /stocks 호출 시 조회하여 `StockInfo.changeRate`에 포함.
- 장 마감 후 서버 재시작 시에도 KIS REST API fallback으로 복구됨.

---

### 2.2 `report:{stockCode}:text` — AI 투자 리포트 캐시

```
Key   : report:005930:text
Value : "{\"title\":\"삼성전자 투자 리포트\",...}" (JSON 문자열)
자료구조: String
TTL   : 43200초 (12시간)
```
- **캐시 무효화(우선순위 1순위)**: 뉴스 수집 스케줄러가 해당 종목의 신규 뉴스를 `news_sentiments`에 INSERT하는 시점에 이 키를 즉시 `DEL`한다. 12시간 TTL은 무효화 로직이 어떤 이유로든 발동하지 않았을 때의 보조 안전장치일 뿐이다.
- 캐시 미스 시 `ai_investment_reports` 최신 레코드를 대신 반환(§API 명세서 5.1)하며, 이 경우 자동으로 Redis에 다시 채워 넣지 않는다(새 리포트 생성은 `/refresh` 트리거로만 발생).

---

### 2.2-2 `rate:report:refresh:{stockCode}` — AI 리포트 새로고침 레이트리밋 (v10 신규)

```
Key   : rate:report:refresh:005930
Value : "1"
자료구조: String (SET ... NX EX 30)
TTL   : 30초
```
- `POST /reports/stocks/{stockCode}/refresh` 요청 시 RabbitMQ 발행 직전 `SET NX EX 30`으로 락 획득을 시도한다.
- 락 획득 실패(이미 존재) → `CustomException(REPORT_REFRESH_RATE_LIMITED)` → HTTP 429(`api.md §5.4 E4290`).
- §2.6 `rate:order:lock`과 동일하게 유저 단위가 아닌 **종목 단위**로 잠근다 — 여러 유저가 동시에 같은 종목을 새로고침해도 워커 중복 실행을 막기 위함.
- 이 락이 도입되기 전에는 프론트가 요청 완료 즉시 버튼을 재활성화해 같은 종목이 짧은 시간에 여러 번 큐잉되는 문제가 있었다(프론트 로딩 상태 버그와는 별개의 서버측 방어선).

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
- `ConditionMatchingEngine.execute()`가 주문을 시도하기 직전 `SET rate:order:lock:{userId}:{stockCode} locked NX EX 4`로 락 획득을 시도한다. (RedisKeys.rateOrderLock(userId, stockCode) 사용 — v4)
- 락 획득 실패(이미 존재) → 해당 주문 시도는 스킵(동시 중복 주문 방지).
- **이 락은 무한매수 방지의 2차 방어선이다.** 1차 방어선은 `trading_conditions.is_active`를 체결 성공 시 `FALSE`로 전환하는 로직이며, 이 락은 "같은 순간에 여러 워커/스레드가 동시에 같은 조건을 처리하는 경우"만 방지한다. `is_active` 전환 로직이 실패하거나 지연되는 경우까지 이 락 하나로 막을 수는 없으므로, 두 로직을 항상 함께 구현한다.

---

### 2.6-2 `rate:condition:retryCount:{conditionId}` / `rate:condition:backoff:{conditionId}` — 자동매매 체결 실패 재시도 (v10 신규)

```
Key   : rate:condition:retryCount:42
Value : 실패 누적 횟수 (문자열 정수, INCR)
자료구조: String
TTL   : 5분 (첫 실패 시 설정, 그 안에서만 누적)

Key   : rate:condition:backoff:42
Value : "1"
자료구조: String (SET NX EX 10)
TTL   : 10초
```
- **배경**: 예전엔 `ConditionMatchingEngine.execute()`가 `assetService.executeOrder()`의 성공/실패를 확인하지 않고 무조건 `is_active=false`로 전환했다. clinerules.md §4.2가 명시적으로 금지한 "`has_triggered`식 영구 잠김"이 주문 실패(KIS 장애, 레이트리밋 등) 시에도 그대로 재현되는 버그였다.
- **수정된 동작**: 주문이 `FILLED`일 때만 `is_active`를 끈다(§2.6과 동일한 "성공 시에만" 원칙). 실패하면 `rate:condition:retryCount:{conditionId}`를 INCR하고, 곧바로 재시도하지 않도록 `rate:condition:backoff:{conditionId}`를 10초간 세팅한다 — 시세 틱마다 재평가되는 조건이 실패 직후 매 틱 KIS를 두들기는 걸 막기 위함. `evaluateConditionsForStock`이 이 백오프 키가 있으면 해당 조건을 건너뛴다.
- **최대 재시도**: 5회(`ConditionMatchingEngine.MAX_ORDER_RETRY`) 누적 실패하면 그때 `is_active=false`로 전환하고 카운터를 지운다 — clinerules.md §4.2의 "재시도 큐로 재발행하거나, 재시도 정책(최대 횟수, 백오프)을 Consumer에 명시" 중 후자 방식을 별도 RabbitMQ 큐 신설 없이 Redis만으로 구현한 것이다(자동매매 체결 자체가 큐가 아니라 `@TransactionalEventListener` 동기 호출 구조이기 때문).
- 체결 성공 시 `retryCount` 키는 즉시 DEL한다.

---

### 2.7 KIS 전역 호출 레이트리미터 — `KisRateLimiter` (v9: Redis 설계 → 인메모리 구현으로 확정)

> **[v9] 설계 변경**: 최초 설계는 아래 `rate:kis:global:orderCount:{epochSecond}` Redis INCR 슬라이딩 카운터였으나, 실제 구현은 이 키를 사용하지 않고 애플리케이션 인스턴스 내 **인메모리 `Semaphore` 기반 leaky-bucket**(`infrastructure.kis.KisRateLimiter`)으로 대체됐다. 아래에 실제 동작을 문서화한다.

```
컴포넌트: infrastructure.kis.KisRateLimiter (Redis 키 없음 — 인메모리 java.util.concurrent.Semaphore)
동작    : permit 2개, @Scheduled(500ms)마다 최대 permit까지 재충전 → 초당 최대 약 4건으로 호출량 제한
acquire : tryAcquire(3초 타임아웃) — 획득 실패 시 CustomException(INTERNAL_SERVER_ERROR) 발생 (큐잉/재시도 없음)
```
- **적용 범위**: 원 설계는 "주문 실행 클라이언트"(KIS 주문 API)로 범위를 한정했으나, 실제로는 `KisChartClient`(일봉/분봉/현재가 조회)와 `KisOrderClient`(주문) 전체 REST 호출에 공통 적용한다 — `RedisPriceClient.getCurrentPrice()`의 캐시미스 폴백처럼 다수 종목을 순회하며 KIS REST를 호출하는 경로의 버스트도 함께 방어하기 위함.
- **알려진 한계 (다중 인스턴스 미대응)**: 인메모리 방식이므로 서버가 2대 이상으로 스케일아웃되면 인스턴스별로 독립된 한도가 적용되어 전체 합산 호출량이 KIS 한도를 넘을 수 있다. 현재는 서버가 1대로 운영되는 전제하에 채택한 설계이며, §2.8의 `ws:session:{userId}`와 마찬가지로 스케일아웃 시점에 원래 설계였던 Redis INCR 방식(또는 Redis 기반 분산 세마포어)으로 교체가 필요하다.
- **한도 초과 시 동작**: 원 설계의 "다음 초까지 대기열(RabbitMQ 재발행/재시도)"과 달리, 현재 구현은 3초 대기 후에도 permit을 못 얻으면 예외를 던지고 호출부(`AssetService.executeOrder` 등)의 기존 실패 처리 경로(`markFailed` 등)로 흡수된다. 별도 재시도 큐는 아직 없다.
- `RedisKeys.rateKisOrderCount()`는 위 Redis 설계를 위해 미리 만들어졌던 키 헬퍼로, 현재는 사용되지 않는다(코드상 유지, 향후 다중 인스턴스 전환 시 재사용 후보).

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
| `rate:report:refresh:{stockCode}` | 자연 만료(TTL 30초) | 자동 소멸, 별도 DEL 불필요 |
| `rate:condition:retryCount:{conditionId}` | 체결 성공 또는 자연 만료(TTL 5분) | 성공 시 즉시 DEL, 그 외엔 자동 소멸 |
| `rate:condition:backoff:{conditionId}` | 자연 만료(TTL 10초) | 자동 소멸, 별도 DEL 불필요 |
| `price:{stockCode}:changeRate` | 자연 만료(TTL 24시간) + WebSocket 실시간 덮어쓰기 | 자동 소멸 or 덮어쓰기 |
| `auth:{userId}:refreshToken` | 로그아웃, Refresh Token Rotation, 토큰 탈취 의심 | 즉시 DEL 또는 덮어쓰기 |
| `auth:{jti}:blacklist` | 자연 만료(TTL) | 자동 소멸 |
| `kis:auth:*` | 스케줄러 선제 갱신 | 덮어쓰기 (기존 값 자동 대체) |
| `rate:order:lock:*` | 자연 만료(TTL 4초) | 자동 소멸, 별도 DEL 불필요 |
| ~~`rate:kis:global:orderCount:*`~~ | (v9: 미사용) | 인메모리 `KisRateLimiter`가 대체 — §2.7 참고 |
| `ws:session:*` (도입 시) | WebSocket `onClose` | 즉시 DEL, TTL은 보조 안전장치 |

---

## 4. 구현 시 체크리스트

- [ ] 모든 Key는 본 문서의 패턴을 그대로 사용하며, 팀/Agent가 임의로 접두사나 구분자를 바꾸지 않는다.
- [ ] `price`, `kis:auth` 키는 애플리케이션 전역 단일 값이며 `userId`를 포함하지 않는다 (혼동 방지).
- [ ] `report` 캐시 무효화 로직은 뉴스 저장 트랜잭션과 같은 흐름(또는 직후 이벤트)에서 반드시 함께 처리한다.
- [ ] 자동매매 무한 반복 방지는 `is_active`(1차) + `rate:order:lock`(2차) 두 겹으로 구현하며, 락만으로 충분하다고 가정하지 않는다.
- [ ] `kis:auth:*` TTL(5시간50분)은 KIS 권고 갱신주기(6시간)보다 반드시 짧게 유지한다.
- [ ] `ws:session:*`은 지금 구현하지 않으며, 스케일아웃 시점에 Pub/Sub 브로드캐스트 방식을 우선 검토한다.
- [ ] `price:{stockCode}:changeRate`는 장 마감 후 서버 재시작 시에도 KIS REST API fallback으로 복구된다.