# 🤖 실시간 AI 모의투자 시스템 (Invest AI)

> **실시간 금융 데이터 파이프라인 + AI 기반 감성 분석/RAG 리포트 자동 생성 시스템**

국내 대장주 종목을 대상으로 실시간 시세를 수집하고, AI가 뉴스를 분석하여 투자자에게 맞춤형 인사이트를 제공하는 모의투자 플랫폼입니다.

---

## 🏗️ 시스템 아키텍처

```
[KIS Websocket] ──(실시간 체결가 push)──> [Spring 시세 클라이언트] ──SET──> [Redis]
                                                                              │
                                                                              ▼
[네이버 뉴스 API] ──(5분 주기 요약 수집)──> [Spring 스케줄러] ──publish──> [RabbitMQ]
                                                                              │
                                                                    ┌─────────┘
                                                                    ▼
                                                            [RabbitListener Worker]
                                                            ① OpenAI 감성 분석
                                                            ② OpenAI 임베딩 생성
                                                            ③ MySQL news_sentiments 저장
                                                                              │
                                                                              ▼
                                                    [조건 매칭 워커] ── 조건 충족 시 ──┬── WebSocket 알림
                                                                                    └── 모의투자 주문 실행
```

---

## 🛠️ 기술 스택

| 영역 | 기술 | 버전/비고 |
|------|------|-----------|
| **Backend** | Spring Boot | 3.5.16 / Java 17 |
| **RDBMS** | MySQL | 9.0+ (`VECTOR(1536)` 지원) |
| **Cache** | Redis | Alpine |
| **Message Queue** | RabbitMQ | 3-management |
| **Authentication** | OAuth 2.0 (Google/Kakao) + JWT | Access + Refresh Token Rotation |
| **Real-time Stock** | KIS (한국투자증권) Open API | **WebSocket 구독(push)** 방식 |
| **News** | 네이버 뉴스 검색 API | 요약(description) 기반, 크롤링 없음 |
| **AI** | OpenAI API | GPT-4o-mini (감성 분석 + 임베딩) |
| **Vector Search** | MySQL Native `VECTOR(1536)` | 별도 Vector DB 없음 |

---

## 📦 프로젝트 구조

```
invest-ai/
├── server/                          # Spring Boot 백엔드
│   ├── docs/                        # 📚 프로젝트 문서
│   │   ├── clinerules.md            # AI Agent 개발 지침서 (v2)
│   │   ├── database.md              # DB 테이블 설계 (v3)
│   │   ├── workflow.md              # 전체 워크플로우 (v2)
│   │   ├── kisflow.md               # KIS API 연동 플로우
│   │   ├── redisflow.md             # Redis Key 설계
│   │   └── api.md                   # API 명세서 (v1)
│   ├── src/main/java/.../
│   │   ├── config/                  # RabbitMQ 설정
│   │   ├── domain/                  # 도메인별 패키지
│   │   │   ├── asset/               # 자산 (Wallet + Holding)
│   │   │   ├── report/              # 리포트
│   │   │   ├── trade/               # 매매
│   │   │   └── user/                # 회원
│   │   ├── global/                  # 공통 유틸
│   │   │   ├── common/              # ApiResponse
│   │   │   └── error/               # 예외 처리
│   │   ├── infra/                   # 인프라 연동
│   │   │   ├── config/              # RedisKeys, RedisConfig, WebClientConfig
│   │   │   ├── openai/              # OpenAI 연동
│   │   │   ├── rabbitmq/            # RabbitMQ 연동
│   │   │   └── scheduler/           # 네이버 뉴스 스케줄러
│   │   ├── kis/test/                # KIS 연동 테스트 (main)
│   │   └── resources/
│   │       └── application.yml      # 설정 파일
│   └── src/test/java/.../
│       ├── kis/KisApiTest.java      # KIS API 통합 테스트
│       └── openai/NewsAiIntegrationTest.java  # OpenAI 통합 테스트
├── front/                           # 프론트엔드
├── docker-compose.yaml              # Docker 환경 (MySQL 9.0 + Redis + RabbitMQ)
└── README.md
```

---

## 🗄️ 데이터베이스 (8개 테이블)

```
users ──1:1── user_wallets
users ──1:N── user_holdings ──N:1── stocks
users ──1:N── trading_conditions ──N:1── stocks
users ──1:N── trading_histories ──N:1── stocks (condition_id nullable)
stocks ──1:N── news_sentiments (VECTOR(1536))
users ──1:N── ai_investment_reports
```

### 변경 이력 (v1 → v2 → v3)

| 변경 | v1 | v2/v3 |
|------|----|-------|
| 자산 구조 | `user_assets` 1개 (현금/주식 혼합) | `user_wallets` + `user_holdings` 분리 |
| 매매 조건 로직 | 없음 | `condition_logic`(AND/OR) 추가 |
| 주문 상태 | `has_triggered` boolean | `status`(PENDING/FILLED/FAILED) + 재시도 |
| 뉴스 데이터 | 본문(content) 전제 | 요약(content_summary) 기반 |
| Vector DB | Chroma/Pinecone | MySQL `VECTOR(1536)` 단일화 |
| CHECK 제약 | 느슨함 | 값-타입 쌍 단위 강제 (`chk_conditions_type_pair`) |

---

## 🔄 8단계 빌드 파이프라인

| 단계 | 내용 | 상태 |
|------|------|------|
| **1** | DB 설계 및 구축 (MySQL 9.x + VECTOR) | ✅ 완료 |
| **2** | OAuth 2.0 로그인 + JWT 인증 | ❌ 미구현 |
| **3-A** | KIS Websocket 실시간 시세 수집 | 🔵 테스트 완료 |
| **3-B** | 네이버 뉴스 수집 스케줄러 | ✅ 완료 |
| **4** | Redis 캐싱 계층 | 🔵 설정 완료 |
| **5** | RabbitMQ 메시지 큐 | 🔵 설정 완료 |
| **6** | AI 감성 분석 + 임베딩 | 🔵 테스트 완료 |
| **7** | 조건 매칭 + WebSocket 알림 | ❌ 미구현 |
| **8** | RAG 리포트 생성 | ❌ 미구현 |

---

## 🔑 Redis Key 설계 (9개)

| Key | TTL | 용도 |
|-----|-----|------|
| `price:{stockCode}:current` | 없음 | 실시간 체결가 (KIS Websocket → SET) |
| `report:{stockCode}:text` | 12시간 | AI 리포트 캐시 |
| `auth:{userId}:refreshToken` | 14일 | JWT Refresh Token |
| `auth:{jti}:blacklist` | Access Token 잔여시간 | 로그아웃 블랙리스트 |
| `kis:auth:accessToken` | 5h50m | KIS Access Token |
| `kis:auth:approvalKey` | 5h50m | KIS WebSocket 접속키 |
| `rate:order:lock:{userId}:{stockCode}` | 4초 | 자동매매 동시성 락 |
| `rate:kis:global:orderCount:{epochSecond}` | 2초 | KIS 호출 레이트리미터 |
| `ws:session:{userId}` | 30분 | (보류) WebSocket 라우팅 |

---

## 🚀 로컬 개발 환경

### 1. Docker 컨테이너 실행

```bash
docker compose up -d
# MySQL 9.0 (3306) + Redis (6379) + RabbitMQ (5672, 15672)
```

### 2. 환경 변수 설정

```bash
# .env 파일 생성 (server/.env)
OPENAI_API_KEY=sk-...
KIS_APP_KEY=...
KIS_APP_SECRET=...
NAVER_CLIENT_ID=...
NAVER_CLIENT_SECRET=...
```

### 3. 서버 실행

```bash
cd server
./gradlew bootRun
```

### 4. 테스트 실행

```bash
# KIS API 테스트 (토큰 발급 + 현재가 조회)
./gradlew test --tests "com.example.invest_ai.kis.KisApiTest"

# OpenAI 감성 분석 테스트
./gradlew test --tests "com.example.invest_ai.openai.NewsAiIntegrationTest"
```

---

## 📋 API 엔드포인트 (v1)

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/auth/login` | ❌ | OAuth 로그인 + JWT 발급 |
| POST | `/api/v1/auth/refresh` | 쿠키 | Access Token 재발급 |
| POST | `/api/v1/auth/logout` | ✅ | 로그아웃 |
| GET | `/api/v1/assets` | ✅ | 지갑+보유종목 종합 조회 |
| POST | `/api/v1/assets/orders` | ✅ | 수동 매매 주문 |
| GET | `/api/v1/assets/histories` | ✅ | 매매 이력 조회 |
| POST | `/api/v1/conditions` | ✅ | 자동 매매 조건 등록 |
| GET | `/api/v1/conditions` | ✅ | 조건 목록 조회 |
| DELETE | `/api/v1/conditions/{id}` | ✅ | 조건 삭제 |
| GET | `/api/v1/reports/stocks/{code}` | ✅ | 최신 리포트 조회 |
| POST | `/api/v1/reports/stocks/{code}/refresh` | ✅ | 리포트 재생성 요청 |
| WS | `/ws` | ✅ | 실시간 알림 구독 |

---

## ⚙️ 주요 설계 결정 사항

- **시세 수집**: REST polling ❌ → **KIS WebSocket 구독(push)** ✅ (API 호출 제한 회피, 저지연)
- **Vector DB**: Chroma/Pinecone ❌ → **MySQL VECTOR(1536)** ✅ (단일화, 운영 단순화)
- **뉴스 데이터**: 뉴스 본문 크롤링 ❌ → **네이버 API 요약(description)** ✅ (IP 차단 리스크 회피)
- **자산 구조**: 단일 `user_assets` ❌ → **Wallet + Holding 분리** ✅ (FK 정합성, 쿼리 단순화)
- **주문 상태**: `has_triggered` boolean ❌ → **PENDING/FILLED/FAILED** ✅ (재시도 가능)
- **에러 처리**: DB 제약조건명 기반 **CustomException 1:1 매핑** ✅ (예외 추적 용이)

---

## 📄 프로젝트 문서

`server/docs/` 디렉토리에서 모든 상세 문서를 확인할 수 있습니다:

| 문서 | 버전 | 설명 |
|------|------|------|
| [clinerules.md](server/docs/clinerules.md) | v2 | AI Agent 개발 지침서 |
| [database.md](server/docs/database.md) | v3 | DB 테이블 설계 + DDL |
| [workflow.md](server/docs/workflow.md) | v2 | 전체 워크플로우 + 아키텍처 |
| [kisflow.md](server/docs/kisflow.md) | - | KIS API 인증/시세 플로우 |
| [redisflow.md](server/docs/redisflow.md) | v1 | Redis Key 설계 |
| [api.md](server/docs/api.md) | v1 | API 명세서 |

---

## 🔗 관련 링크

- [KIS Developers (한국투자증권)](https://developers.koreainvestment.com)
- [네이버 개발자 센터](https://developers.naver.com)
- [OpenAI API](https://platform.openai.com)