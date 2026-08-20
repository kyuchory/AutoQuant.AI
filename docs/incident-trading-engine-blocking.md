# 🔧 [장애 기록] 자동매매 엔진 — 시세 수신 스레드 블로킹 & 중복 주문 레이스 컨디션

> 코드 리뷰 중 발견 → 재현 근거 확보 → 수정 → 실제 부팅 로그로 검증까지의 기록.
> 프로덕션 장애가 실제로 터진 건 아니고, "거래 체결 경로를 꼼꼼히 점검해달라"는 요청으로 코드를 정독하다가
> 발동 조건이 갖춰지면 반드시 터지는 구조적 문제를 찾아낸 사례다.

## 배경

KIS(한국투자증권) 실시간 시세 → 자동매매 조건 매칭 → 주문 체결까지 이어지는 경로를 점검하던 중,
"이론상 위험한 코드"가 아니라 **조건만 갖춰지면 100% 재현되는 구조적 결함**이 두 가지 발견됐다. 둘 다 근본
원인이 같았다: **KIS 주문 API 호출(`.block()`)이 이벤트 처리 스레드 위에서 동기로 실행되고 있었다.**

## 문제 1 — 실시간 시세 수신 스레드가 주문 응답을 기다리며 멈춤

### 증상이 될 수 있었던 흐름

```
KisWebsocketClient (Reactor Netty 이벤트 루프 스레드)
  └─ session.receive().doOnNext(handleMessage)   ← 10개 종목 실시간 체결 데이터가 이 스레드로 옴
       └─ processPipeData()
            └─ eventPublisher.publishEvent(PriceUpdatedEvent)   ← 비동기 설정 없음 = 같은 스레드에서 즉시 실행
                 └─ ConditionMatchingEngine.onPriceUpdated()
                      └─ execute()
                           └─ assetService.executeOrder()
                                └─ KisOrderClient.executeOrder()
                                     └─ rateLimiter.acquire()   ← 최대 3초 대기
                                     └─ webClient...block()     ← 최대 5초 타임아웃
```

조건 하나만 발동돼도 **같은 스레드**가 최대 8초간 블로킹됐다. 그 사이 이 스레드가 처리해야 할 **10개 감시
종목의 실시간 체결 데이터가 전부 멈춘다.** 웹소켓 연결 자체는 살아있으니 겉으로는 "연결됨" 상태인데
실제로는 아무 데이터도 안 들어오는, 진단하기 까다로운 유형의 장애다. 게다가 30초 무데이터 감지 로직
(`checkConnectionHealth`)이 이걸 "연결 끊김"으로 오판해서 불필요한 강제 재연결까지 겹칠 수 있었다.

### 원인

이벤트 발행부(`KisWebsocketClient`)와 리스너부(`ConditionMatchingEngine`) 둘 다 비동기 경계가 전혀 없었다.
Spring의 `ApplicationEventPublisher.publishEvent()`는 기본적으로 **동기** 호출이라, 별도 설정이 없으면
리스너가 발행자와 같은 스레드에서 즉시 실행된다.

## 문제 2 — Redis 동시성 락(4초) < 주문 응답 최악 대기시간(8초)

`ConditionMatchingEngine.execute()`는 같은 유저+종목 조합의 중복 체결을 막으려고 Redis 락을 걸어둔다
("2차 방어선"이라는 주석까지 있었다). 그런데 락 TTL이 **4초**였고, 그 락이 지키는 작업
(`KisOrderClient.executeOrder`)의 최악 소요시간은 **레이트리미터 대기 3초 + HTTP 타임아웃 5초 = 최대 8초**였다.

즉 KIS 응답이 4~8초 사이에 걸리면:

1. t=0s: 조건 발동 → 락 획득(TTL 4초) → KIS 주문 요청 시작
2. t=4s: 락 자동 만료 (주문은 아직 KIS 응답 대기 중)
3. t=4~8s 사이 같은 종목 가격 틱이 또 들어오면 → 같은 조건이 다시 매칭 → 락을 다시 획득 가능 → **같은 매수/매도 주문이 중복 발사**

방어선이라는 이름을 달고 있었지만, 정작 그게 막아야 할 시나리오(느린 KIS 응답)에서 뚫리는 구조였다.

## 수정

두 문제 모두 "블로킹 호출이 이벤트 처리 스레드를 막는다"는 하나의 근본 원인에서 나왔기 때문에, 스레드
경계를 하나 그어주는 것으로 함께 해결했다.

### 1) 전용 스레드풀 + `@Async` 경계 추가

[`AsyncConfig.java`](../server/src/main/java/com/example/invest_ai/config/AsyncConfig.java) 신설:

```java
@Bean(CONDITION_MATCHING_EXECUTOR)
public Executor conditionMatchingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("cond-match-");
    executor.initialize();
    return executor;
}
```

[`ConditionMatchingEngine.java`](../server/src/main/java/com/example/invest_ai/domain/trade/service/ConditionMatchingEngine.java)의
두 이벤트 리스너에 `@Async(AsyncConfig.CONDITION_MATCHING_EXECUTOR)`를 추가:

- `onPriceUpdated` — KIS 웹소켓 수신 스레드를 더 이상 블로킹하지 않음
- `onNewsSentimentSaved` — 같은 이유로 RabbitMQ 뉴스 큐 리스너 스레드도 보호

### 2) 락 TTL을 실제 최악 대기시간보다 넉넉하게

`rateOrderLock`의 TTL을 4초 → **10초**로 상향 (`KisOrderClient` 최악 소요시간 8초 + 여유 2초).
[`RedisKeys.java`](../server/src/main/java/com/example/invest_ai/infra/config/RedisKeys.java)의 문서 주석도 함께 갱신.

### 3) (덤) DB 인덱스 점검

리뷰 중 `trading_conditions`에 `(stock_code, is_active)` 복합 인덱스가 없다는 것도 발견했다. 이 테이블은
가격 틱마다(10종목 × 초당 여러 번) `findAllByStockCodeAndIsActiveTrue()`로 조회되는, 거래 엔진에서 가장
빈번하게 실행되는 쿼리다. 확인해보니 `stock_code` 단일 컬럼 인덱스는 FK 제약 때문에 이미 존재해서
"풀스캔"까지는 아니었지만, `is_active` 필터링을 인덱스 안에서 끝낼 수 있도록 복합 인덱스를 추가했다.

```sql
CREATE INDEX idx_conditions_stock_active ON trading_conditions(stock_code, is_active);
```

로컬 Docker MySQL(`invest_db`)에 직접 적용 후 `docs/database.md` DDL에도 반영. 기존 `stock_code` 단일
인덱스는 새 복합 인덱스의 왼쪽 prefix로 커버되어 MySQL이 자동으로 정리했다.

### 4) 트레일링 스탑 `targetValue` 부호 검증 추가

처음 리뷰 때 "낮은 우선순위"로 남겨뒀던 이슈였는데, 이어서 마저 고쳤다. `ConditionService.validateTriggers()`가
`baseType`/`compareType`/`isRate`는 검증하면서 정작 `targetValue`가 음수인지는 검증하지 않았다. 트레일링
스탑은 "고점 대비 -N%면 매도" 방식이라 targetValue가 음수여야 하는데, 0 이상이 들어가면 임계값이 고점과
같거나 높아져 `현재가 <= 임계값`(BELOW)이 사실상 항상 참이 된다 — `isPersistent=true` 조건이면 매 틱마다
반복 매도가 나갈 수 있는 실제 금전적 위험이었다.

```java
if (tr.targetValue() == null || tr.targetValue().compareTo(BigDecimal.ZERO) >= 0) {
    throw new CustomException(ErrorCode.INVALID_TRIGGER,
        "트레일링 스탑의 targetValue는 고점 대비 하락률(음수)이어야 합니다. 예: -5");
}
```

### 5) AND 결합 시 트레일링 고점 갱신이 트리거 순서에 의존하던 문제 수정

`ConditionMatchingEngine.evaluate()`가 AND 로직에서 앞 트리거가 불일치하면 즉시 `return false`로
빠져나오는데, 트레일링 스탑의 고점 갱신은 원래 `evaluateTrigger()` 안에 있었다. 그래서 AND로 묶인 여러
트리거 중 앞쪽이 불일치하면, 그 뒤에 놓인 트레일링 스탑 트리거는 이번 틱에 아예 평가되지 않아 고점 갱신을
놓쳤다 — 트리거를 등록한 순서에 따라 트레일링 스탑의 정확도가 달라지는 은근한 버그였다.

고점 갱신 로직을 `updateTrailingHighestIfNeeded()`로 분리해서, AND/OR 매칭 루프를 돌기 **전에 모든
트리거**에 대해 먼저 실행하도록 바꿨다. 이제 매칭 결과(단락 평가)와 무관하게 고점은 항상 최신 상태로
유지된다.

## 검증

수정 후 실제로 앱을 부팅해서 로그로 확인했다 (`./gradlew bootRun`, MySQL/Redis/RabbitMQ 로컬 Docker 연동):

```
23:07:44.589  [ntContainer#0-1] NewsAnalysisWorker : [뉴스 워커] 캐시 무효화 ...
23:07:44.589  [   cond-match-1] ConditionMatchingEngine : [매매 엔진] 신규 뉴스 AI 점수 수신 ...
```

`NewsAnalysisWorker`(RabbitMQ 리스너 스레드, `ntContainer#0-1`)와 `ConditionMatchingEngine`
(`cond-match-1`)이 **서로 다른 스레드**에서 도는 것이 로그로 확인됐다 — 뉴스 큐 리스너가 더 이상 조건
매칭/주문 실행을 기다리며 블로킹되지 않는다는 뜻이다. KIS 웹소켓 쪽도 동일한 경로(`@Async`)를 타므로
같은 효과가 적용된다.

- `SHOW INDEX FROM trading_conditions;` 로 `idx_conditions_stock_active` 생성 확인
- `./gradlew compileJava` 정상 통과
- 앱 정상 기동, KIS 토큰/승인키 발급 + 웹소켓 10종목 구독 정상 확인

## 회귀 테스트로 고정

같은 버그가 재발해도 리뷰에 의존하지 않고 바로 잡히도록, 고칠 수 있는 것 위주로 테스트를 남겼다
(`ConditionMatchingEngineTest`, `ConditionServiceTest`).

- `@Async`가 `onPriceUpdated`/`onNewsSentimentSaved`에서 제거되면 실패 (리플렉션으로 어노테이션 존재 확인)
- 주문 동시성 락 TTL이 10초 미만으로 되돌아가면 실패
- AND 결합에서 앞 트리거가 불일치해도 트레일링 스탑 고점이 갱신되는지 확인
- 트레일링 스탑 `targetValue`가 0 이상이면 등록 자체가 거부되는지 확인

DB 인덱스(3번)는 스키마 상태라 이 테스트 스위트로는 고정하지 않았다 — `SHOW INDEX`로 수동 확인.

## 변경 파일

- `server/src/main/java/com/example/invest_ai/config/AsyncConfig.java` (신규)
- `server/src/main/java/com/example/invest_ai/domain/trade/service/ConditionMatchingEngine.java`
- `server/src/main/java/com/example/invest_ai/domain/trade/service/ConditionService.java`
- `server/src/main/java/com/example/invest_ai/infra/config/RedisKeys.java`
- `server/src/test/java/com/example/invest_ai/domain/ConditionMatchingEngineTest.java` (신규)
- `server/src/test/java/com/example/invest_ai/domain/ConditionServiceTest.java` (신규)
- `docs/database.md` (인덱스 DDL 추가)
- MySQL `invest_db.trading_conditions` — `idx_conditions_stock_active` 인덱스 추가 (직접 적용)

## 타임라인 요약

| 순서 | 이슈 | 심각도 | 상태 |
|---|---|---|---|
| 1 | KIS 웹소켓 수신 스레드가 주문 응답을 기다리며 블로킹 | 🔴 높음 | ✅ 수정 (`@Async`) |
| 2 | Redis 락 TTL(4s) < 주문 최악 대기시간(8s) → 중복 주문 레이스 | 🔴 높음 | ✅ 수정 (TTL 10s) |
| 3 | `trading_conditions(stock_code, is_active)` 복합 인덱스 누락 | 🟡 중간 | ✅ 수정 (인덱스 추가) |
| 4 | 트레일링 스탑 `targetValue` 부호 미검증 | 🟡 중간 | ✅ 수정 (검증 추가) |
| 5 | AND 결합 시 트레일링 고점 갱신이 트리거 순서에 의존 | 🟢 낮음 | ✅ 수정 (평가 순서 분리) |

리뷰에서 발견한 5건 모두 수정 완료.
