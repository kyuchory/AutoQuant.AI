🤖 SYSTEM PROMPT: AI Agent를 위한 실시간 AI 투자 시스템 개발 지침서 (v2)

> 개정 사항(v2): 테이블 설계 v3 / 워크플로우 v2 확정 내용 반영 — Wallet/Holding 분리, `condition_logic`(AND/OR), 주문 `status`(PENDING/FILLED/FAILED) 재시도 흐름, KIS Websocket 기반 시세 수집, 제약조건 이름 기반 예외 매핑, 외부 API 클라이언트 패키지 컨벤션 명시. 변경/신규 항목은 본문에 **[v2]** 로 표시.

---

1. 프로젝트 개요 (Project Overview)

본 프로젝트는 "실시간 금융 데이터 파이프라인 및 AI 기반 감성 분석/RAG 리포트 자동 생성 시스템"이다. 사용자가 설정한 주가 조건 및 AI 감성 점수 조건이 일치하는 순간 실시간 알림을 송출하고 가상 모의투자 API를 통해 자동 매매를 체결한다. 대용량 트래픽 완충 및 AI 비용 최적화를 위해 캐시(Redis)와 메시지 큐(RabbitMQ)를 결합한 이벤트 기반 아키텍처(Event-Driven Architecture)를 지향하며, 국내 시가총액 상위 대장주 종목으로 데이터 범위를 제한한다.

**[v2] 시세 데이터는 REST polling이 아닌 한국투자증권(KIS) Open API의 Websocket 구독(push) 방식으로 수집한다.** 뉴스 데이터는 네이버 뉴스 검색 API에서 제공하는 요약(`description`)을 사용하며 본문 전체를 크롤링하지 않는다.

---

2. 아키텍처 및 레이어드 패턴 규칙 (Layered Architecture Rules)

Spring Boot의 표준 레이어드 및 도메인 기반 패키지 패턴을 엄격히 준수한다. 각 레이어는 단방향 의존성(Controller ➔ Service ➔ Repository)을 가지며, 역할이 뒤섞여서는 안 된다.

**2.1. Controller Layer (.controller)**
- 역할: HTTP 요청 매핑, 요청 데이터 유효성 검증(@Valid), HTTP 상태 코드 및 공통 응답 포맷 반환.
- 금지 사항: 비즈니스 로직 포함 금지, 엔티티(Entity) 직접 반환 절대 금지.
- 규칙: 반드시 요청(Request DTO)을 받고 공통 응답 객체(ApiResponse<T>)를 반환한다.

**2.2. Service Layer (.service)**
- 역할: 실제 비즈니스 로직 수행, 트래픽 제어, 트랜잭션 관리(@Transactional).
- 규칙: 단순 조회가 아닌 핵심 로직(CUD)은 @Transactional을 명시한다. 외부 API 호출, Redis 캐싱, RabbitMQ 발행(Publish) 로직은 비즈니스 흐름에 맞게 서비스 레이어에서 제어하되, 세부 구현은 외부 컴포넌트(infrastructure 또는 client)에 위임한다.
- **[v2] 자산 관련 서비스는 `WalletService`(현금)와 `HoldingService`(보유종목)로 역할을 분리한다.** 두 개를 하나의 `AssetService`로 합치지 않는다. 총자산 조회처럼 두 개를 조합해야 하는 유스케이스는 별도의 조회 전용 서비스(예: `AssetSummaryService`)에서 두 서비스를 조합한다.

**2.3. Repository Layer (.repository)**
- 역할: 데이터베이스(MySQL)와의 직접적인 I/O 인터페이스.
- 규칙: Spring Data JPA를 기본으로 사용하며, 무거운 통계나 복잡한 동적 쿼리는 Querydsl 인터페이스를 분리하여 구현한다.

**2.4. Domain/Model Layer (.entity)**
- 역할: JPA 엔티티 및 핵심 도메인 객체 정의.
- 규칙: 엔티티는 Setter 사용을 절대 금지하며, 의미 있는 비즈니스 메서드를 통해 상태를 변경한다. 생성자는 @Builder 패턴을 사용하여 무분별한 객체 생성을 막는다.
- **[v2] 자산은 `Wallet`(예수금)과 `Holding`(보유종목) 두 개의 독립된 엔티티로 정의한다.** 예시 비즈니스 메서드: `Wallet.deposit(amount)`, `Wallet.withdraw(amount)`, `Holding.increaseQuantity(qty, price)`, `Holding.decreaseQuantity(qty)`. 기존 예시였던 `updateAssetBalance()`처럼 하나의 엔티티가 현금과 수량을 동시에 다루는 메서드는 사용하지 않는다.
- **[v2] `TradingHistory` 엔티티는 `status`(PENDING/FILLED/FAILED) 상태를 가지며, 상태 전이 메서드(`markFilled(price, quantity)`, `markFailed(reason)`)를 통해서만 상태를 변경한다.**

**2.5. Data Transfer Object (.dto)**
- 역할: 레이어 간 데이터 전송을 위한 불변(Immutable) 객체.
- 규칙: record 키워드 사용을 절대 원칙으로 하며, Inner Class 구조를 활용하여 하나의 파일 안에서 Request와 Response를 묶어서 관리한다.
- **[v2] `TradingConditionRequest`는 `targetPrice`, `priceConditionType`, `targetAiScore`, `aiConditionType`, `conditionLogic`(AND/OR) 필드를 모두 포함한다.** DB의 `chk_conditions_type_pair` CHECK 제약과 동일한 규칙(가격 조건을 쓰려면 `targetPrice`+`priceConditionType`이 함께 있어야 하고, AI 점수 조건을 쓰려면 `targetAiScore`+`aiConditionType`이 함께 있어야 함)을 `@AssertTrue` 커스텀 검증 메서드로 DTO 레벨에서도 먼저 걸러낸다. DB CHECK 위반으로 인한 500 에러가 사용자에게 그대로 노출되지 않도록, 애플리케이션단 검증을 1차 방어선으로 둔다.

**2.6. Global Error Layer (global/error)**
- 역할: 애플리케이션 전역에서 발생하는 예외 통합 관리.
- 규칙: 비즈니스 로직 예외 발생 시 커스텀 예외(CustomException)를 던지고, @RestControllerAdvice 클래스에서 이를 가로채 ApiResponse 규격으로 클라이언트에게 반환한다. 개별 비즈니스 로직에 지저분한 try-catch를 지양한다.
- **[v2] MySQL 제약조건 이름과 CustomException을 1:1로 매핑한다.** `DataIntegrityViolationException` 발생 시 원인(cause) 메시지에서 제약조건 이름을 추출하여 아래와 같이 변환한다:

| 제약조건 이름 | 매핑되는 CustomException | 사용자 메시지 예시 |
|---|---|---|
| `uk_holdings_user_stock` | `DuplicateHoldingException` | "이미 보유 중인 종목입니다." |
| `uk_news_url` | (내부 로직에서 Skip 처리, 예외로 노출하지 않음) | - |
| `uk_users_email` | `DuplicateEmailException` | "이미 가입된 이메일입니다." |
| `chk_conditions_type_pair` | `InvalidConditionException` | "조건 값과 조건 타입을 함께 입력해야 합니다." |

---

3. 명명 규칙 및 코딩 컨벤션 (Naming & Coding Conventions)

AI Agent는 코드를 생성할 때 다음 네이밍 규칙을 100% 준수해야 한다.

**3.1. 기본 표기법**
- 클래스/인터페이스: UpperCamelCase (예: StockConditionService)
- 메서드/변수: lowerCamelCase (예: currentPrice, calculateSentimentScore())
- 상수(Constants): UPPER_SNAKE_CASE (예: MAX_RETRIES_COUNT)
- 패키지명: 소문자 및 단어 결합 (예: com.example.invest_ai.domain.report)
- 데이터베이스(MySQL): snake_case (예: user_id, condition_id)
- **[v2] DB 제약조건명: `uk_<table>_<column(s)>`, `chk_<table>_<desc>` (예: uk_holdings_user_stock, chk_conditions_type_pair). 테이블 설계 문서 v3의 §0-1 컨벤션과 동일하게 유지한다.**

**3.2. 레이어별 클래스 명명 규칙**
- Controller: *Controller (예: AuthController, AssetController)
- Service: *Service (인터페이스), *ServiceImpl (구현체 - 복잡한 비즈니스 분리 시에만 사용하며 단일 로직은 인터페이스 없이 클래스로 작성 가능)
- Repository: *Repository (예: UserRepository)
- **[v2] 외부 API 클라이언트: `*Client` (예: KisWebsocketClient, NaverNewsClient, OpenAiClient)**

**3.3. 메서드 접두사 규칙 (기능적 통일)**
- 조회: 단건은 get* (예: getUser()), 복수 건 또는 조건 검색은 find* (예: findActiveConditions())
- 생성: create* (예: createCondition())
- 수정: update* (예: updateAsset())
- 삭제: delete* (예: deleteCondition())
- **[v2] 상태 전이(도메인 이벤트성 변경): mark* (예: markFilled(), markFailed())**

---

4. 인프라 활용 컴포넌트 규칙 (Infrastructure Layer Rules)

**4.1. Redis 활용 규칙**
- 초당 트래픽이 높은 데이터 및 비싼 AI 분석 리포트 결과물은 Redis 캐시를 거친다.
- Key 네이밍 규칙: Domain:Identifier:DataType 구조를 따른다. (예: report:005930:text, price:005930:current)
- 데이터 정합성 규칙: 뉴스 수집 스케줄러가 특정 종목의 새로운 뉴스를 MySQL에 추가(Insert)하는 시점에, Redis에 존재하는 해당 종목의 AI 분석 리포트 캐시 키를 강제로 파기(delete)하는 '캐시 무효화(Cache Invalidation)' 로직을 반드시 구현한다.
- **[v2] 실시간 시세 키(`price:{stockCode}:current`)는 스케줄러가 아닌 §4.4의 KIS Websocket 클라이언트가 데이터를 수신할 때마다 즉시 SET한다.**

**4.2. RabbitMQ 활용 규칙**
- AI 뉴스 분석 및 RAG 리포트 동적 생성 등 연산 부하가 큰 작업은 MQ를 통해 비동기로 처리한다.
- 구조: Exchange ➔ Queue ➔ Consumer 구조를 명확히 설계한다.
- Consumer 메서드는 @RabbitListener를 선언하고 비동기 워커 형태로 동작하며, 예외 발생 시 메시지 유실 방지 대책을 고려한다.
- **[v2] 자동 매매 체결 워커는 주문 시도 시 `TradingHistory`를 `status='PENDING'`으로 먼저 생성한 뒤 체결 API를 호출한다.** 성공 시 `markFilled()`로 `status='FILLED'` 전이, 실패 시 `markFailed(reason)`으로 `status='FAILED'` 전이 및 `failureReason` 기록. `FAILED` 건은 재시도 큐(예: `trading-retry-queue`)로 재발행하거나, 재시도 정책(최대 횟수, 백오프)을 Consumer에 명시한다. `trading_conditions.has_triggered`와 같은 영구 잠김 방식은 사용하지 않는다.

**4.3. MySQL Vector & Spring AI 활용 규칙**
- RAG 구현을 위해 외부 Vector DB를 쓰지 않고, MySQL의 네이티브 `VECTOR(1536)` 컬럼 규격을 활용한다.
- 자바 서버단에서는 `Spring AI` 라이브러리의 `ChatModel` 및 `VectorStore` 인터페이스를 주입받아 임베딩 생성 및 유사도 검색(`similaritySearch`)을 처리한다.
- **[v2] RAG에 활용되는 뉴스 텍스트는 네이버 뉴스 API의 `description`(요약) 필드이며 본문 전체가 아니다. 프롬프트 조립 시 "아래는 뉴스 요약이며 본문 전체가 아니므로 요약에 없는 세부 수치를 임의로 생성하지 말라"는 지시를 포함한다.**

**4.4. 외부 API 클라이언트 규칙 [v2 신규]**
- KIS(한국투자증권) 및 네이버 뉴스 API처럼 서비스 외부의 데이터 소스와 통신하는 컴포넌트는 `infrastructure` 패키지 하위에 소스별로 분리하여 위치시킨다.
  - `com.example.invest_ai.infrastructure.kis` — `KisAuthClient`(REST 토큰/Websocket 접속키 발급), `KisWebsocketClient`(실시간 체결가 구독 및 수신)
  - `com.example.invest_ai.infrastructure.naver` — `NaverNewsClient`(뉴스 검색 API 호출)
  - `com.example.invest_ai.infrastructure.openai` — `OpenAiClient` 또는 Spring AI `ChatModel`/`EmbeddingModel` 래퍼
- `KisWebsocketClient`는 연결 종료 감지 시 자동 재연결 후 구독 목록(모니터링 대상 종목)을 재등록하는 로직을 반드시 포함한다.
- Service 레이어는 이 클라이언트들을 주입받아 사용하되, 각 외부 API의 요청/응답 세부 포맷(인증 헤더, 페이로드 구조 등)을 Service 레이어에 노출하지 않는다.

---

5. 핵심 API 설계 목록 (Core API Endpoint Blueprint)

URI는 복수형 명사를 사용하며 소문자로 작성한다. 버전 식별자 `/api/v1`을 필수로 접두사에 붙인다.

**5.1. 회원 및 인증 (Authentication)**
- POST /api/v1/auth/login : 소셜 로그인 인증 및 JWT 발급
- POST /api/v1/auth/logout : 토큰 만료 처리 (Redis 블랙리스트 등록)

**5.2. 가상 모의투자 잔고 및 체결 (Assets) [v2 수정]**
- GET /api/v1/assets : **유저의 지갑(Wallet) 잔고와 보유 종목(Holdings) 목록을 조합하여 응답한다.** 응답 DTO(`AssetSummaryResponse`)는 `walletBalance`(예수금)와 `holdings`(보유종목 리스트 + 실시간 평가금액)를 별도 필드로 구성하며, 내부적으로 `WalletService`와 `HoldingService`를 조합해 만든다. 실시간 평가금액은 Redis의 `price:{stockCode}:current` 키를 조회해 계산한다.
- POST /api/v1/assets/orders : 수동 매매 주문. 응답 시 생성된 `TradingHistory`의 `status`(PENDING → 체결 결과에 따라 FILLED/FAILED)를 포함한다.
- **자동 매매는 조건 매칭 워커(KIS Websocket 이벤트 리스너 + Redis/MySQL 조건 평가)에 의해 트리거되며, 별도 스케줄러(polling)로 구현하지 않는다.**

**5.3. 실시간 알림 및 자동 매매 조건 설정 (Conditions) [v2 수정]**
- POST /api/v1/conditions : 유저 맞춤 주가/AI 스코어 자동 매매 감시 조건 등록. **요청 DTO는 `targetPrice`, `priceConditionType`, `targetAiScore`, `aiConditionType`, `conditionLogic`(AND/OR)을 포함하며, §2.5의 값-타입 쌍 검증을 통과해야 한다.**
- GET /api/v1/conditions : 내가 설정한 조건 목록 조회 (JWT의 userId 기반)
- DELETE /api/v1/conditions/{conditionId} : 특정 알림/매매 조건 삭제

**5.4. AI 투자 리포트 및 RAG (Reports)**
- GET /api/v1/reports/stocks/{stockCode} : 특정 종목의 AI 최신 투자 리포트 조회 (Redis 캐시 우선 조회전략 적용)
- POST /api/v1/reports/stocks/{stockCode}/refresh : 유저가 새로고침 버튼 클릭 시 발동. Redis 캐시를 우회하고 RabbitMQ에 즉시 새 리포트 생성 티켓을 발행하는 비동기 트리거 API

---

6. AI Agent 자가 검증 체크리스트 (Agent Verification Checklist)

AI Agent는 코드를 출력하기 전, 다음 질문에 모두 "Yes"라고 답할 수 있는지 내부적으로 검증 프로세스를 거쳐야 한다.

**기존 항목**
- [ ] 생성한 클래스나 변수의 네이밍이 3항의 컨벤션(CamelCase, 접두사 규칙)에 완벽히 부합하는가?
- [ ] Controller에 비즈니스 로직이 들어가거나 Entity를 직접 반환하고 있지는 않은가?
- [ ] 모든 Entity 클래스에 Setter가 배제되고 @Builder가 적용되었는가?
- [ ] DTO 설계 시 Java의 `record` 키워드를 사용하고 Request/Response 구조를 이쁘게 묶었는가?
- [ ] 실시간 뉴스가 추가될 때 오래된 레디스 캐시를 파기하는 무효화 로직이 누락되지 않았는가?
- [ ] RAG 구현 시 외부 Vector DB 대신 MySQL Vector(1536) 컬럼 명세를 정확히 따르고 있는가?

**[v2 신규 항목]**
- [ ] Wallet(현금)과 Holding(보유종목)을 하나의 엔티티/테이블/서비스로 합치지 않았는가?
- [ ] 매매 조건 DTO에 `conditionLogic`(AND/OR)과 값-타입 쌍 검증(@AssertTrue 등)이 포함되었는가?
- [ ] 자동 매매 체결 로직이 `status`(PENDING/FILLED/FAILED) 상태 전이와 실패 시 재시도 경로를 갖추고 있는가? (`has_triggered` boolean 방식으로 회귀하지 않았는가?)
- [ ] 시세 수집을 REST polling이 아닌 KIS Websocket 이벤트 기반으로 구현했는가? 재연결 시 구독 재등록 로직이 있는가?
- [ ] 뉴스 텍스트를 "본문"이 아닌 "요약(description)"으로 전제하고 프롬프트/엔티티를 설계했는가?
- [ ] MySQL 제약조건 위반 예외가 제약조건 이름 기준으로 CustomException에 매핑되었는가?
- [ ] 외부 API 클라이언트(KIS, 네이버, OpenAI)가 `infrastructure` 패키지 하위에 소스별로 분리되어 있는가?

---

[명령] 이상의 규칙을 숙지하고, 이후 요청되는 모든 Spring Boot 및 MySQL 관련 백엔드 코드 작성 및 아키텍처 설계 요구에 이 프롬프트의 지침을 절대적으로 준수하여 정밀한 코드를 생성하라.