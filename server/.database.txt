# 실시간 AI 모의투자 시스템 — 테이블 설계 문서 (v3)

> 개정 사항(v2): `user_assets` → `user_wallets` + `user_holdings` 분리 / `trading_conditions`에 `condition_logic`(AND/OR) 추가 / `has_triggered` boolean → `status` 상태값으로 변경 / `news_sentiments.content` → `content_summary`로 정정 (네이버 API는 요약만 제공)
>
> 개정 사항(v3): `trading_conditions`의 CHECK 제약을 조건-타입 쌍 단위로 촘촘하게 보완 / 모든 UNIQUE·CHECK 제약조건에 `uk_`, `chk_` 네이밍 컨벤션을 적용해 명시적으로 이름 부여 (JPA 매핑 및 예외 핸들링 대비)

---

## 0. 개정 이력

| 테이블 | 문제점 | 변경 | 버전 |
|---|---|---|---|
| `user_assets` | 현금/주식을 한 테이블에서 폴리모픽하게 관리 → FK 모순, 조건문 남발 | `user_wallets`(현금) / `user_holdings`(보유 종목)로 분리 | v2 |
| `trading_conditions` | 가격 조건 + AI 점수 조건 동시 설정 시 AND/OR 판단 불가 | `condition_logic` 컬럼 추가 | v2 |
| `trading_conditions` | `has_triggered` boolean → 실패 시 영구 잠김, 재시도 불가 | `trading_histories.status`로 상태 관리 이전 | v2 |
| `news_sentiments` | `content`를 "본문"으로 정의했으나 네이버 API는 요약만 제공 | `content_summary`로 컬럼명·설명 정정 | v2 |
| 전체 | Chroma/Pinecone 등 별도 Vector DB 전제 | MySQL `VECTOR(1536)` 단일화 확정, 문서상 혼선 제거 | v2 |
| `trading_conditions` | CHECK가 "둘 중 하나는 NOT NULL"만 강제 → `target_price`만 넣고 `price_condition_type`을 빠뜨려도 통과됨 | 조건-타입 쌍 단위로 CHECK 재정의 (`chk_conditions_type_pair`) | v3 |
| 전체 테이블 | UNIQUE/CHECK 제약조건에 이름이 없거나 일관성 없음 → JPA 예외 핸들링 시 원인 식별 어려움 | `uk_<table>_<column(s)>`, `chk_<table>_<desc>` 컨벤션으로 전체 통일 | v3 |

---

## 0-1. 제약조건 네이밍 컨벤션 (v3 신규)

JPA `@Table(uniqueConstraints=...)` 매핑, `DataIntegrityViolationException` 예외 핸들링에서 제약조건 이름으로 분기 처리할 수 있도록 아래 규칙을 전 테이블에 일괄 적용한다.

```
uk_<table>_<column(s)>   -- UNIQUE 제약
fk_<table>_<ref_table>   -- FOREIGN KEY 제약 (필요 시 명시)
chk_<table>_<desc>       -- CHECK 제약
```

예: 종목 중복 보유 방지 시 `UK_HOLDINGS_USER_STOCK` 예외를 캐치해 "이미 보유 중인 종목입니다" 메시지를 매핑하는 식으로 활용한다.

---

## 1. ERD 개요 (텍스트)

```
users ──1:N── user_wallets
users ──1:N── user_holdings ──N:1── stocks
users ──1:N── trading_conditions ──N:1── stocks
users ──1:N── trading_histories ──N:1── stocks
                    trading_histories ──N:1── trading_conditions (nullable, 수동매매는 NULL)
stocks ──1:N── news_sentiments
users ──1:N── ai_investment_reports
```

---

## 2. 테이블 상세 설계

### ① 회원 테이블 (users)

**역할**: OAuth(구글/카카오) 로그인 정보를 담는 기준 테이블. JWT 발급 시 `user_id`가 토큰에 포함되어 모든 API에서 사용자를 식별.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| user_id | BIGINT | PK, AUTO_INCREMENT | 내부 식별용 유저 ID |
| email | VARCHAR(100) | UNIQUE, NOT NULL | 유저 이메일 |
| nickname | VARCHAR(50) | NOT NULL | 유저 닉네임 |
| provider | VARCHAR(20) | NOT NULL | OAuth 제공자 (GOOGLE, KAKAO) |
| provider_id | VARCHAR(100) | NOT NULL | OAuth사 고유 식별자 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 가입 일시 |

```sql
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider UNIQUE (provider, provider_id)
);
```

---

### ② 시스템 종목 마스터 테이블 (stocks)

**역할**: 실시간으로 추적할 종목 코드를 관리하는 기준 테이블. KIS Websocket 구독 목록과 네이버 뉴스 검색 키워드 모두 이 테이블 기준으로 동적 결정.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| stock_code | VARCHAR(10) | PK | 종목 코드 (예: 005930) |
| stock_name | VARCHAR(50) | NOT NULL | 종목명 (예: 삼성전자) |
| is_monitored | BOOLEAN | DEFAULT TRUE | KIS Websocket 구독 및 뉴스 수집 대상 여부 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 등록 일시 |

```sql
CREATE TABLE stocks (
    stock_code VARCHAR(10) PRIMARY KEY,
    stock_name VARCHAR(50) NOT NULL,
    is_monitored BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### ③ 유저 현금 지갑 테이블 (user_wallets) — v2 신규 분리

**역할**: 유저별 모의투자 현금 예수금만 관리. 기존 `user_assets`에서 `CASH` 로우로 처리하던 부분을 별도 테이블로 분리해 FK 모순과 조건 분기를 제거.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| wallet_id | BIGINT | PK, AUTO_INCREMENT | 지갑 레코드 ID |
| user_id | BIGINT | FK(users.user_id), UNIQUE, NOT NULL | 지갑 소유자 (유저당 1개) |
| balance | DECIMAL(18,4) | DEFAULT 0.0000 | 보유 현금 예수금 |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 최종 갱신 일시 |

```sql
CREATE TABLE user_wallets (
    wallet_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance DECIMAL(18,4) DEFAULT 0.0000,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

---

### ④ 유저 보유 종목 테이블 (user_holdings) — v2 신규 분리

**역할**: 유저별 실제 보유 종목만 관리. `stocks`를 정상적으로 FK 참조할 수 있음 (v1의 `CASH` 값 같은 예외 케이스 없음).

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| holding_id | BIGINT | PK, AUTO_INCREMENT | 보유 종목 레코드 ID |
| user_id | BIGINT | FK(users.user_id), NOT NULL | 보유자 |
| stock_code | VARCHAR(10) | FK(stocks.stock_code), NOT NULL | 종목 코드 |
| quantity | INT | DEFAULT 0 | 보유 수량 |
| average_price | DECIMAL(18,4) | DEFAULT 0.0000 | 매입 평균 단가 |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 최종 갱신 일시 |
| 복합 제약 | - | `uk_holdings_user_stock` UNIQUE(user_id, stock_code) | 유저당 종목별 중복 로우 방지 (제약조건 이름 명시 — v3) |

```sql
CREATE TABLE user_holdings (
    holding_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    quantity INT DEFAULT 0,
    average_price DECIMAL(18,4) DEFAULT 0.0000,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_holdings_user_stock UNIQUE (user_id, stock_code),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);
```

> **JPA/예외 핸들링 활용 예시**: `user_holdings`에 이미 존재하는 `(user_id, stock_code)` 조합으로 INSERT를 시도하면 `DataIntegrityViolationException`이 발생하고, 그 원인(cause) 메시지에 `uk_holdings_user_stock`이 포함된다. 이 제약조건 이름을 기준으로 예외를 분기해 "이미 보유 중인 종목입니다" 같은 사용자 메시지를 매핑할 수 있다. 이는 워크플로우 문서 5단계의 `news_url` 유니크 제약 기준 중복 Skip 로직과 동일한 패턴이다.

> **총자산 계산 예시**: `user_wallets.balance + SUM(user_holdings.quantity * 현재가(Redis 조회))`. v1처럼 `CASE WHEN stock_code='CASH'` 분기를 쓸 필요가 없어져 쿼리가 단순해짐.

---

### ⑤ 유저 자동 매매 조건 테이블 (trading_conditions)

**역할**: 유저가 설정한 자동 매매 조건 저장. 가격 조건과 AI 점수 조건을 **동시에** 설정할 경우를 대비해 `condition_logic`(AND/OR)을 명시적으로 저장.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| condition_id | BIGINT | PK, AUTO_INCREMENT | 조건 식별 ID |
| user_id | BIGINT | FK(users.user_id) | 조건 설정 유저 |
| stock_code | VARCHAR(10) | FK(stocks.stock_code) | 대상 종목 |
| target_price | DECIMAL(18,4) | NULL | 감시할 목표 가격 |
| price_condition_type | VARCHAR(10) | NULL | ABOVE / BELOW |
| target_ai_score | INT | NULL | 감시할 목표 AI 감성 점수 (0~100) |
| ai_condition_type | VARCHAR(10) | NULL | ABOVE / BELOW |
| **condition_logic** | **VARCHAR(3)** | **NOT NULL, DEFAULT 'AND'** | **가격 조건과 AI 점수 조건을 동시 설정 시 AND/OR 판단 기준 (v2 신규)** |
| order_type | VARCHAR(10) | NOT NULL | BUY / SELL |
| order_quantity | INT | NOT NULL | 조건 충족 시 주문 수량 |
| is_active | BOOLEAN | DEFAULT TRUE | 조건 감시 활성화 여부 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 조건 생성 일시 |

> `has_triggered` 컬럼은 v2에서 **제거**. 트리거 여부·재시도 관리는 `trading_histories.status`로 이전(§⑥ 참조). `is_active`만으로 "이 조건을 계속 감시할지"를 결정하며, 1회성 조건이 필요하면 애플리케이션 레벨에서 체결 성공 시 `is_active = FALSE`로 업데이트.

> **v3 보완 — CHECK 제약 촘촘화**: v2의 `CHECK (target_price IS NOT NULL OR target_ai_score IS NOT NULL)`은 "가격 또는 AI 점수 중 하나는 입력되어야 한다"만 강제할 뿐, 예를 들어 `target_price`만 넣고 짝이 되는 `price_condition_type`(ABOVE/BELOW)을 비워도 통과된다. 이 상태로 워커가 `price_condition_type`을 읽으면 NULL을 만나 조건 평가 로직이 깨진다. 따라서 v3는 "값-타입 쌍" 단위로 CHECK를 재정의해, 가격 조건을 쓰려면 `target_price`와 `price_condition_type`이 함께 있어야 하고, AI 점수 조건을 쓰려면 `target_ai_score`와 `ai_condition_type`이 함께 있어야 하도록 DB 레벨에서 강제한다.
>
> ⚠️ MySQL은 8.0.16 이전 버전에서 `CHECK` 제약을 파싱만 하고 실제로 강제하지 않는다(무시됨). 반드시 8.0.16 이상에서만 이 제약이 유효하므로, 개발/운영 환경의 MySQL 버전을 명확히 고정해야 한다(예: `mysql:8.4`).

```sql
CREATE TABLE trading_conditions (
    condition_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    target_price DECIMAL(18,4) NULL,
    price_condition_type VARCHAR(10) NULL COMMENT 'ABOVE, BELOW',
    target_ai_score INT NULL,
    ai_condition_type VARCHAR(10) NULL COMMENT 'ABOVE, BELOW',
    condition_logic VARCHAR(3) NOT NULL DEFAULT 'AND' COMMENT 'AND, OR - 두 조건 동시 설정 시 판단 기준',
    order_type VARCHAR(10) NOT NULL COMMENT 'BUY, SELL',
    order_quantity INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code),
    CONSTRAINT chk_conditions_type_pair CHECK (
        (target_price IS NOT NULL AND price_condition_type IS NOT NULL) OR
        (target_ai_score IS NOT NULL AND ai_condition_type IS NOT NULL)
    )
);
```

> 애플리케이션 로직 예시:
> - 가격 조건만 설정 → `condition_logic` 무시, 가격 조건만 평가.
> - 가격+AI 점수 모두 설정, `condition_logic='AND'` → 두 조건 모두 만족해야 발동.
> - 가격+AI 점수 모두 설정, `condition_logic='OR'` → 둘 중 하나만 만족해도 발동.
> - 조건이 1개만 설정된 상태에서 `condition_logic='OR'`이 저장되어도 DB가 막지는 않는다(비교 대상이 하나뿐이라 의미가 없을 뿐 오류는 아님). 이 부분은 CHECK로 강제하기보다 애플리케이션/프론트엔드에서 "조건 1개 설정 시 `condition_logic` 입력 UI 비활성화"로 처리하는 편이 제약조건 복잡도 대비 실익이 크다.

---

### ⑥ 매매/체결 히스토리 테이블 (trading_histories)

**역할**: 실제 체결 내역(자동+수동)을 기록하는 영구 장부. v2에서는 `status` 필드를 추가해 주문의 생명주기(대기중/체결완료/실패)를 관리, 실패 시 재시도가 가능하도록 함.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| history_id | BIGINT | PK, AUTO_INCREMENT | 체결 이력 ID |
| user_id | BIGINT | FK(users.user_id) | 주문 주체 유저 |
| condition_id | BIGINT | FK(trading_conditions.condition_id), NULL | 발동 조건 (수동 매매는 NULL) |
| stock_code | VARCHAR(10) | FK(stocks.stock_code) | 종목 코드 |
| order_type | VARCHAR(10) | NOT NULL | BUY / SELL |
| **status** | **VARCHAR(10)** | **NOT NULL, DEFAULT 'PENDING'** | **PENDING / FILLED / FAILED (v2 신규 — 기존 has_triggered 대체)** |
| execution_price | DECIMAL(18,4) | NULL | 실제 체결 가격 (PENDING 상태에서는 NULL 가능) |
| execution_quantity | INT | NULL | 실제 체결 수량 |
| total_amount | DECIMAL(18,4) | NULL | 총 체결 금액 |
| failure_reason | VARCHAR(255) | NULL | 실패 시 사유 (v2 신규) |
| requested_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 주문 요청 일시 (v2 신규) |
| executed_at | TIMESTAMP | NULL | 체결 완료 일시 |

```sql
CREATE TABLE trading_histories (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    condition_id BIGINT NULL,
    stock_code VARCHAR(10) NOT NULL,
    order_type VARCHAR(10) NOT NULL COMMENT 'BUY, SELL',
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, FILLED, FAILED',
    execution_price DECIMAL(18,4) NULL,
    execution_quantity INT NULL,
    total_amount DECIMAL(18,4) NULL,
    failure_reason VARCHAR(255) NULL,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (condition_id) REFERENCES trading_conditions(condition_id),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);
```

> **재시도 흐름 예시**: 조건 매칭 워커가 주문을 시도 → `status='PENDING'`으로 레코드 생성 → 체결 API 호출 → 성공 시 `status='FILLED'` + `executed_at` 갱신, 실패 시 `status='FAILED'` + `failure_reason` 기록 후 일정 시간 뒤 재시도 가능. 기존 `trading_conditions.has_triggered = TRUE` 방식은 실패해도 영구히 막혀버리는 문제가 있었으나, v2 구조는 `FAILED` 건에 대해 재주문 로직을 붙일 수 있음.

---

### ⑦ AI 뉴스 감성 분석 및 RAG 테이블 (news_sentiments)

**역할**: 수집된 뉴스 요약 및 AI 분석 결과 저장. MySQL `VECTOR(1536)` 컬럼으로 임베딩 유사도 검색을 직접 수행 (별도 Vector DB 없음).

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| news_id | BIGINT | PK, AUTO_INCREMENT | 뉴스 고유 ID |
| stock_code | VARCHAR(10) | FK(stocks.stock_code) | 관련 종목 코드 |
| news_url | VARCHAR(500) | `uk_news_url` UNIQUE, NOT NULL | 뉴스 원본 링크 (중복 수집 방지 키, 제약조건 이름 명시 — v3) |
| title | VARCHAR(255) | NOT NULL | 뉴스 제목 |
| **content_summary** | **TEXT** | **NOT NULL** | **네이버 뉴스 API `description` 필드 (요약, 통상 100~200자). 본문 전체 아님 (v2 정정)** |
| sentiment | VARCHAR(10) | NOT NULL | AI 감성 결과 (GOOD/BAD/NEUTRAL) |
| ai_score | INT | NOT NULL | AI가 매긴 호재 점수 (0~100) |
| ai_reason | TEXT | NULL | AI가 해당 점수를 준 요약 이유 |
| published_at | TIMESTAMP | NOT NULL | 뉴스 원 발행 시간 |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 시스템 수집 일시 |
| embedding | VECTOR(1536) | NULL | OpenAI 임베딩 벡터 (RAG 검색용, MySQL 네이티브 VECTOR 타입) |

```sql
CREATE TABLE news_sentiments (
    news_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL,
    news_url VARCHAR(500) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_summary TEXT NOT NULL COMMENT '네이버 뉴스 API description 필드 - 요약이며 본문 전체 아님',
    sentiment VARCHAR(10) NOT NULL COMMENT 'GOOD, BAD, NEUTRAL',
    ai_score INT NOT NULL,
    ai_reason TEXT NULL,
    published_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    embedding VECTOR(1536) NULL,
    CONSTRAINT uk_news_url UNIQUE (news_url),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code),
    VECTOR INDEX idx_embedding (embedding)
);
```

> **JPA/예외 핸들링 활용 예시**: 스케줄러가 이미 수집된 `news_url`로 재삽입을 시도하면 `uk_news_url` 제약 위반 예외가 발생한다. 이 이름을 기준으로 "이미 수집된 기사이므로 Skip" 로직을 명확히 분기할 수 있다 (워크플로우 문서 5단계 언급 내용과 동일 패턴).

> `VECTOR INDEX` 문법은 MySQL 버전 및 벤더별로 차이가 있을 수 있으므로, 실제 구축 시 사용 중인 MySQL 버전의 벡터 인덱스 지원 여부와 정확한 DDL 문법을 별도 확인 필요.

---

### ⑧ AI 투자 맞춤 리포트 테이블 (ai_investment_reports)

**역할**: RabbitMQ Worker가 RAG 파이프라인을 거쳐 생성한 유저별 맞춤 리포트 본문을 영구 저장.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| report_id | BIGINT | PK, AUTO_INCREMENT | 리포트 식별 ID |
| user_id | BIGINT | FK(users.user_id) | 리포트 수신 유저 |
| report_content | TEXT | NOT NULL | AI 생성 리포트 본문 (Markdown 포맷) |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 리포트 생성 일시 |

```sql
CREATE TABLE ai_investment_reports (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    report_content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

---

## 3. 전체 DDL 통합본 (실행 순서 고려)

```sql
-- 1. 기준 테이블
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider UNIQUE (provider, provider_id)
);

CREATE TABLE stocks (
    stock_code VARCHAR(10) PRIMARY KEY,
    stock_name VARCHAR(50) NOT NULL,
    is_monitored BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 자산 관련 (분리된 구조)
CREATE TABLE user_wallets (
    wallet_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance DECIMAL(18,4) DEFAULT 0.0000,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE user_holdings (
    holding_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    quantity INT DEFAULT 0,
    average_price DECIMAL(18,4) DEFAULT 0.0000,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_holdings_user_stock UNIQUE (user_id, stock_code),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);

-- 3. 매매 조건 및 이력
CREATE TABLE trading_conditions (
    condition_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    target_price DECIMAL(18,4) NULL,
    price_condition_type VARCHAR(10) NULL COMMENT 'ABOVE, BELOW',
    target_ai_score INT NULL,
    ai_condition_type VARCHAR(10) NULL COMMENT 'ABOVE, BELOW',
    condition_logic VARCHAR(3) NOT NULL DEFAULT 'AND' COMMENT 'AND, OR',
    order_type VARCHAR(10) NOT NULL COMMENT 'BUY, SELL',
    order_quantity INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code),
    CONSTRAINT chk_conditions_type_pair CHECK (
        (target_price IS NOT NULL AND price_condition_type IS NOT NULL) OR
        (target_ai_score IS NOT NULL AND ai_condition_type IS NOT NULL)
    )
);

CREATE TABLE trading_histories (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    condition_id BIGINT NULL,
    stock_code VARCHAR(10) NOT NULL,
    order_type VARCHAR(10) NOT NULL COMMENT 'BUY, SELL',
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, FILLED, FAILED',
    execution_price DECIMAL(18,4) NULL,
    execution_quantity INT NULL,
    total_amount DECIMAL(18,4) NULL,
    failure_reason VARCHAR(255) NULL,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (condition_id) REFERENCES trading_conditions(condition_id),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);

-- 4. 뉴스/AI/RAG
CREATE TABLE news_sentiments (
    news_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL,
    news_url VARCHAR(500) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_summary TEXT NOT NULL COMMENT '네이버 뉴스 API description 필드 - 요약',
    sentiment VARCHAR(10) NOT NULL COMMENT 'GOOD, BAD, NEUTRAL',
    ai_score INT NOT NULL,
    ai_reason TEXT NULL,
    published_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    embedding VECTOR(1536) NULL,
    CONSTRAINT uk_news_url UNIQUE (news_url),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);

CREATE TABLE ai_investment_reports (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    report_content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

> 벡터 인덱스(`VECTOR INDEX`) 구문은 사용 중인 MySQL 버전에 따라 문법이 다를 수 있어 통합 DDL에서는 제외했습니다. 실제 구축 단계에서 버전을 확정한 뒤 인덱스 구문을 추가하는 것을 권장합니다.
>
> 위 CHECK 제약(`chk_conditions_type_pair`)은 MySQL 8.0.16 이상에서만 실제로 강제됩니다. 그 이전 버전에서는 파싱만 되고 무시되므로 반드시 버전을 확인하세요.

---

## 4. 버전별 변경 요약표

| 구분 | v1 | v2 | v3 |
|---|---|---|---|
| 자산 테이블 | `user_assets` 1개 (현금/주식 폴리모픽) | `user_wallets` + `user_holdings` 2개로 분리 | (유지) |
| 매매조건 로직 | 없음 (암묵적) | `condition_logic`(AND/OR) 명시 | (유지) |
| 주문 상태 | `has_triggered` boolean | `trading_histories.status`(PENDING/FILLED/FAILED) | (유지) |
| 실패 처리 | 불가 (영구 잠김) | `failure_reason` 기록 + 재시도 가능 | (유지) |
| 뉴스 콘텐츠 | `content`(본문 전제) | `content_summary`(요약, 실제 API 스펙 반영) | (유지) |
| 벡터 저장소 | Chroma/Pinecone 언급 혼재 | MySQL `VECTOR(1536)` 단일화 | (유지) |
| `trading_conditions` CHECK | - | `target_price OR target_ai_score IS NOT NULL`만 강제 (타입 컬럼 누락 허용됨) | 값-타입 쌍 단위로 강제 (`chk_conditions_type_pair`) |
| 제약조건 이름 | - | 일부만 명명(`uk_provider`, `uk_user_stock`) | 전 테이블 `uk_`/`chk_` 컨벤션으로 통일, JPA 예외 핸들링 대비 |