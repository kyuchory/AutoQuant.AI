# 실시간 AI 모의투자 시스템 — 워크플로우 설계 문서 (v2)

> 개정 사항: Vector DB는 MySQL VECTOR 타입으로 통일 / 네이버 뉴스는 요약 데이터 기반 / 시세 수집은 한국투자증권(KIS) Websocket 구독 방식으로 변경

---

## 0. 개정 이력

| 버전 | 변경 내용 |
|---|---|
| v1 | 초안 (Chroma/Pinecone 언급, 뉴스 본문 크롤링 전제, REST polling 시세 수집) |
| v2 | Vector DB → MySQL VECTOR(1536) 단일화 / 뉴스 콘텐츠 → 요약(description) 기반으로 정정 / 시세 수집 → KIS Websocket 구독 방식으로 변경 |

---

## 1. 기술 스택 확정

| 영역 | 기술 | 비고 |
|---|---|---|
| 백엔드 | Spring Boot | REST API + WebSocket 서버 |
| RDBMS | MySQL 9.x | `VECTOR` 타입 지원 — 별도 Vector DB(Chroma/Pinecone) 미사용 |
| 캐시 | Redis | 실시간 시세 캐싱, 분석 결과 캐싱 |
| 메시지 큐 | RabbitMQ | AI 분석 요청 비동기 처리 |
| 인증 | OAuth 2.0(구글/카카오) + JWT | 모든 API의 사용자 식별 기준 |
| 시세 데이터 | 한국투자증권(KIS) Open API — **Websocket** | REST polling 대신 실시간 구독(push) 방식 |
| 뉴스 데이터 | 네이버 뉴스 검색 API | 본문 전체가 아닌 **요약(description)** 제공 — 크롤링 없음 |
| AI 임베딩/분석 | OpenAI Embedding API + Chat API | 감성 분석, 벡터 생성, RAG 리포트 생성 |

> **Vector DB 관련 결정**: 별도 Vector DB(Chroma 등)를 도입하지 않고 MySQL의 `VECTOR(1536)` 컬럼과 벡터 유사도 검색 기능을 그대로 사용한다. 데이터 규모가 커지거나 ANN 인덱스 성능이 필요해지는 시점에 한해 추후 마이그레이션을 검토한다. (v1 문서의 Chroma/Pinecone 언급은 전량 폐기)

> **네이버 뉴스 API 관련 결정**: 네이버 뉴스 검색 API는 기사 본문 전체가 아닌 `description`(요약, 통상 100~200자) 필드만 제공한다. 이는 API 자체의 스펙 한계이며 크롤링으로 원문을 보강하지 않는다(언론사 사이트 무단 크롤링에 따른 IP 차단 리스크 회피가 애초 도입 목적이었기 때문). 따라서 RAG 컨텍스트와 감성 분석은 요약 기준으로 수행하며, 짧은 텍스트에 최적화된 프롬프트 설계가 필요하다.

> **시세 수집 관련 결정**: 업비트(코인)는 사용하지 않고, 국내 주식 대장주 10종목을 KIS Open API로 다룬다. REST 방식으로 1~2초 간격 polling하는 대신, KIS가 공식 지원하는 **Websocket 구독 방식**을 사용한다. 실시간 접속키(approval_key)로 인증 후 종목별 실시간 체결가/호가를 구독하면, 서버가 데이터를 push해주므로 API 호출 횟수 부담이 없고 지연시간도 짧다. REST는 장 시작 시 최초 스냅샷 조회, 계좌/잔고 조회 등 push가 불필요한 곳에만 사용한다.

---

## 2. 8단계 빌드 파이프라인 (수정본)

### 1단계 — 기반 데이터베이스(RDBMS) 설계 및 구축

- MySQL 9.x 설치, `VECTOR` 타입 지원 여부 확인.
- 테이블 스키마 설계 (상세는 별첨 `테이블 설계 문서 v2` 참조).
- 필수 테이블: `users`, `stocks`, `user_wallets`, `user_holdings`, `trading_conditions`, `trading_histories`, `news_sentiments`, `ai_investment_reports`.

### 2단계 — 로그인 및 회원 인증(JWT) 구현

- OAuth 2.0(구글/카카오) 로그인 구현, 인증 성공 시 JWT 토큰 발급.
- 모든 이후 API는 JWT의 `user_id` claim으로 사용자를 식별.
- 검증: Postman으로 로그인 → 토큰 발급 → 다른 API 요청 시 토큰 기반 식별 확인.

### 3단계 — 외부 데이터 연동 기반 마련

**3-A. 시세 데이터 (KIS Websocket)**
- KIS Developers 센터에서 앱키/시크릿 발급.
- REST 접근토큰(access_token) + Websocket 실시간 접속키(approval_key) 발급 로직 구현.
- `stocks` 테이블의 `is_monitored = TRUE`인 종목 코드를 읽어 Websocket 구독 목록 동적 생성.
- 구독 시작 후 실시간 체결가 수신 여부를 로그로 확인.

**3-B. 뉴스 데이터 (네이버 뉴스 검색 API)**
- 네이버 개발자 센터에서 Client ID/Secret 발급.
- `stocks` 테이블의 종목명을 키워드로 5분 주기 스케줄러 구성.
- 응답 필드는 `title`, `description`(요약), `link`, `pubDate`로 구성됨을 전제로 설계.

### 4단계 — Redis 캐싱 계층 추가 (중요 ⚡)

- Redis 설치 및 백엔드 연결.
- **변경점**: 스케줄러가 아닌 KIS Websocket 클라이언트가 실시간 체결가를 수신할 때마다 Redis에 즉시 SET(덮어쓰기).
- 클라이언트가 시세를 요청하면 DB가 아닌 Redis에서 즉시 응답.
- Websocket 연결 끊김 대비: 재연결 시 구독 목록을 자동 재등록하는 로직 필요(KIS 공식 라이브러리 참고).

### 5단계 — 메시지 큐(MQ) 도입

- RabbitMQ 설치.
- 뉴스 수집 데이터를 DB에 바로 저장하지 않고 큐(Producer)에 적재.
- Consumer 워커가 큐에서 뉴스를 하나씩 안전하게 소비하도록 구성.
- 중복 방지: `news_url` 유니크 제약을 기준으로 이미 존재하는 링크는 Skip.

### 6단계 — AI 모델 결합 및 감성 스코어링

- OpenAI API 연결.
- Consumer 워커: 뉴스 요약(description) 텍스트를 AI에 전달 → `sentiment`(GOOD/BAD/NEUTRAL), `ai_score`(0~100) 산출.
- 동일 텍스트를 OpenAI 임베딩 API로 전달 → 1,536차원 벡터 수신 → MySQL `embedding VECTOR(1536)` 컬럼에 저장.
- 결과를 Redis(단기)와 MySQL(영구)에 저장.

### 7단계 — 조건 매칭 및 웹소켓(사용자 알림) 구현

- Socket.io 또는 NestJS/Spring WebSocket으로 사용자용 소켓 서버 오픈. 연결 시 JWT로 사용자 식별.
- 백엔드 워커가 Redis(4단계 시세)와 6단계(AI 감성 점수)를 모니터링.
- `trading_conditions`의 조건과 일치하면:
  1. 사용자에게 실시간 알림 전송.
  2. `condition_logic`(AND/OR)에 따라 가격 조건·AI 점수 조건 평가.
  3. 조건 충족 시 모의투자 주문 로직 호출 → 체결 시도.
  4. 주문 결과는 `status`(PENDING/FILLED/FAILED)로 관리하여 재시도 가능하도록 처리 (기존 `has_triggered` boolean 방식 폐기).

### 8단계 — RAG 리포트 구현

- Chroma 등 별도 Vector DB 설치 단계 **삭제**.
- `news_sentiments.embedding` 컬럼에 이미 저장된 벡터를 그대로 활용.
- 사용자가 리포트를 요청하면 MySQL Vector Search로 관련 뉴스 요약 3~4건을 검색 → `trading_histories`(매매 이력)와 결합 → OpenAI Chat API로 맞춤 리포트 생성 → `ai_investment_reports`에 저장.

---

## 3. 시스템 아키텍처 다이어그램 (텍스트)

```
[KIS Websocket] ──(실시간 체결가 push)──> [Spring 시세 클라이언트] ──(SET)──> [Redis]
                                                                                  │
                                                                                  ▼
[네이버 뉴스 API] ──(5분 주기 요약 수집)──> [Spring 스케줄러] ──(발행)──> [RabbitMQ: news-queue]
                                                                                  │
                                                                    ┌─────────────┘
                                                                    ▼
                                                    [RabbitListener Worker]
                                                    ① OpenAI 감성 분석 (sentiment, ai_score)
                                                    ② OpenAI 임베딩 (VECTOR(1536))
                                                    ③ MySQL news_sentiments 저장
                                                                    │
                                                                    ▼
                                          [조건 매칭 워커] ── Redis(시세) + MySQL(감성점수) 모니터링
                                                    │
                                        조건 일치 시 ──┬── WebSocket 알림 (사용자)
                                                        └── 모의투자 주문 실행 → trading_histories 기록
```

```
[사용자 브라우저] ──①분석 요청──> [Controller] ──②Redis 캐시 확인──> [Redis]
        │                                                              │
        │                                              (Cache Hit) ────┘──> 즉시 응답
        │
        │ (Cache Miss)
        ▼
[RabbitMQ: ai-request-queue] ──③티켓 발행──> [Controller는 즉시 "접수완료" 응답]
        │
        ▼
[RabbitListener Worker]
   ④ MySQL Vector Search로 관련 뉴스 3~4건 검색 (RAG-Retrieval)
   ⑤ 질문 + 뉴스 요약 + 최근 매매이력 → 프롬프트 조립 (RAG-Augmentation)
   ⑥ OpenAI Chat API 호출 (RAG-Generation)
   ⑦ 결과를 ai_investment_reports에 저장 + Redis 캐싱
        │
        ▼
[WebSocket으로 사용자에게 완료 알림 전송]
```

---

## 4. RAG 파이프라인 매핑 (수정본)

| RAG 단계 | 학술적 정의 | 본 프로젝트 구현 |
|---|---|---|
| R (Retrieval) | 질문과 관련된 문서를 벡터 유사도로 검색 | MySQL `VECTOR` 컬럼 기준 유사도 검색으로 뉴스 **요약** 3~4건 검색 |
| A (Augmentation) | 검색 문서 + 질문을 하나의 프롬프트로 조립 | `[뉴스 요약들] + [사용자 질문] + [최근 매매 이력]` 조립 |
| G (Generation) | LLM으로 최종 답변 생성 | OpenAI Chat API 호출 → 리포트 생성 |

> 뉴스가 요약 수준이므로, 프롬프트에는 "아래는 뉴스 요약이며 상세 본문이 아님을 감안해 분석하라"는 지시를 포함해 할루시네이션(요약에 없는 세부사항을 지어내는 것)을 방지한다.

---

## 5. 시세 데이터 흐름 상세 (KIS Websocket 기반)

1. 서버 기동 시 REST로 access_token, Websocket으로 approval_key 발급.
2. `stocks` 테이블에서 `is_monitored = TRUE`인 종목 코드 목록 조회.
3. Websocket 연결 후 종목별 실시간 체결가(H0STCNT0 등) 구독 요청.
4. 데이터 수신 시마다 Redis에 `stock:price:{stock_code}` 키로 최신가 SET.
5. 연결 끊김 감지 시 자동 재연결 + 구독 목록 재등록.
6. 장 마감 시간대에는 구독을 유지하되 신규 데이터가 없으므로 Redis 값이 마지막 체결가로 고정됨(추가 처리 불필요).

---

## 6. 뉴스 수집 파이프라인 상세 (요약 기반)

1. 스케줄러가 5분마다 `stocks.stock_name`을 키워드로 네이버 뉴스 검색 API 호출 (`display=3~5`, `sort=date`).
2. 응답의 `link`를 기준으로 `news_sentiments.news_url` 중복 체크 → 신규 건만 RabbitMQ에 발행.
3. Consumer 워커가 `title` + `description`(요약)을 OpenAI에 전달해 감성 분석 및 임베딩 생성.
4. 결과를 `news_sentiments`에 저장.

---

## 7. 남은 확인 필요 사항 (Open Items)

- KIS Open API의 모의투자 계좌는 REST 호출 제한이 낮으므로, 백테스팅처럼 연속 조회가 많은 기능을 추가할 경우 실전투자 계좌 키 사용을 검토할 것.
- 네이버 뉴스 검색 API는 전체 검색 카테고리 합산 일일 호출 제한이 있으므로, 종목 10개 × 5분 주기 호출량이 한도 내인지 사전 계산 필요.
- `condition_logic`(AND/OR) UI 설계 — 사용자가 가격 조건과 AI 점수 조건을 동시에 설정할 때 프론트엔드에서 명확히 선택하도록 구성.