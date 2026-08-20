# 실시간 AI 모의투자 시스템 — 테이블 설계 문서 (v5)

> 개정 사항(v2): `user_assets` → `user_wallets` + `user_holdings` 분리 / `trading_conditions`에 `condition_logic`(AND/OR) 추가 / `has_triggered` boolean → `status` 상태값으로 변경 / `news_sentiments.content` → `content_summary`로 정정 (네이버 API는 요약만 제공)
>
> 개정 사항(v3): `trading_conditions`의 CHECK 제약을 조건-타입 쌍 단위로 촘촘하게 보완 / 모든 UNIQUE·CHECK 제약조건에 `uk_`, `chk_` 네이밍 컨벤션을 적용해 명시적으로 이름 부여 (JPA 매핑 및 예외 핸들링 대비)
>
> 개정 사항(v4): `trading_conditions`를 "액션(주문)" 중심으로 재편하고, 발동 조건을 `condition_triggers`(트리거) 테이블로 1:N 분리. 이를 통해 고정가격 감시·손절(Stop-Loss)·익절(Take-Profit)·트레일링 스탑(Trailing Stop)·스탑 지정가/시장가·AI 점수 자동매매를 하나의 모델로 통합 확장. 실행 모드(`execution_mode`: AUTO/MANUAL) 지원. (DB는 `ddl-auto: none` 수동 관리로 전환)
>
> 개정 사항(v5): `trading_conditions`에 `is_persistent`(BOOLEAN, 기본 FALSE) 추가. 조건 실행 후 `is_persistent=FALSE`(기본, 1회성)면 기존과 동일하게 `is_active=FALSE`로 비활성화하고, `is_persistent=TRUE`면 실행 후에도 `is_active=TRUE`를 유지해 반복 감시를 지원한다.

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
| `trading_conditions` | 단일 테이블에 "발동 조건"과 "주문 액션"을 혼재 → 손절/익절(평단가 대비 %)·트레일링 스탑(고점 추적)·스탑 주문·AI 자동매매를 표현 불가 | `trading_conditions`(액션) + `condition_triggers`(트리거, 1:N)로 분리. `trigger_type`/`base_type`/`compare_type`/`is_rate` 및 `execution_mode`(AUTO/MANUAL) 도입 | v4 |

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
trading_conditions ──1:N── condition_triggers
stocks ──1:N── news_sentiments
users ──1:N── ai_investment_reports ──N:1── stocks
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

---

### ⑤ 유저 자동 매매 조건 테이블 (trading_conditions) — v4 재편

**역할**: 조건 주문의 **액션(주문)** 부분만 담는 부모 테이블. "언제 발동할지"는 §⑤-1 `condition_triggers`로 분리한다. 실행 방식(`execution_mode`: AUTO/MANUAL)을 지원한다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| condition_id | BIGINT | PK, AUTO_INCREMENT | 조건(주문 액션) 식별 ID |
| user_id | BIGINT | FK(users.user_id), NOT NULL | 조건 설정 유저 |
| stock_code | VARCHAR(10) | FK(stocks.stock_code), NOT NULL | 대상 종목 |
| order_type | VARCHAR(10) | NOT NULL | BUY / SELL |
| order_quantity | INT | NOT NULL | 조건 충족 시 주문 수량 |
| order_price_type | VARCHAR(10) | NOT NULL, DEFAULT 'MARKET' | MARKET / LIMIT |
| limit_price | DECIMAL(18,4) | NULL | LIMIT 주문 시 지정가 (MARKET이면 NULL) |
| condition_logic | VARCHAR(3) | NOT NULL, DEFAULT 'AND' | 복수 트리거 결합 기준 (AND/OR) |
| **execution_mode** | **VARCHAR(10)** | **NOT NULL, DEFAULT 'AUTO'** | **AUTO (완전자동 체결) / MANUAL (반자동 승인 제안)** |
| is_active | BOOLEAN | DEFAULT TRUE | 조건 감시 활성화 여부 |
| **is_persistent** | **BOOLEAN** | **NOT NULL, DEFAULT FALSE** | **TRUE면 조건 충족/실행 후에도 is_active를 유지(반복 감시), FALSE(기본)면 1회 실행 후 자동 비활성화** |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 갱신 일시 |

```sql
CREATE TABLE trading_conditions (
    condition_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    stock_code       VARCHAR(10) NOT NULL,
    order_type       VARCHAR(10) NOT NULL COMMENT 'BUY, SELL',
    order_quantity   INT NOT NULL,
    order_price_type VARCHAR(10) NOT NULL DEFAULT 'MARKET' COMMENT 'MARKET, LIMIT',
    limit_price      DECIMAL(18,4) NULL,
    condition_logic  VARCHAR(3) NOT NULL DEFAULT 'AND' COMMENT 'AND, OR',
    execution_mode   VARCHAR(10) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO, MANUAL',
    is_active        BOOLEAN DEFAULT TRUE,
    is_persistent    BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'TRUE면 실행 후에도 반복 감시 유지',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code),
    CONSTRAINT chk_conditions_order_type CHECK (order_type IN ('BUY','SELL')),
    CONSTRAINT chk_conditions_order_price_type CHECK (order_price_type IN ('MARKET','LIMIT')),
    CONSTRAINT chk_conditions_logic CHECK (condition_logic IN ('AND','OR')),
    CONSTRAINT chk_conditions_execution_mode CHECK (execution_mode IN ('AUTO','MANUAL')),
    CONSTRAINT chk_conditions_limit_price CHECK (
        (order_price_type = 'LIMIT' AND limit_price IS NOT NULL) OR
        (order_price_type = 'MARKET' AND limit_price IS NULL)
    )
);
```

---

### ⑤-1 유저 자동 매매 발동 조건 테이블 (condition_triggers) — v4 신규

**역할**: 조건 주문의 **트리거(발동 조건)** 부분. 하나의 `trading_conditions`가 복수 개의 트리거를 가질 수 있으며(N:1), `condition_logic`에 따라 AND/OR로 결합된다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| trigger_id | BIGINT | PK, AUTO_INCREMENT | 트리거 식별 ID |
| condition_id | BIGINT | FK(trading_conditions.condition_id), NOT NULL, ON DELETE CASCADE | 소속 조건 |
| trigger_type | VARCHAR(20) | NOT NULL | PRICE / PROFIT_TARGET / STOP_LOSS / TRAILING_STOP / AI_SCORE |
| base_type | VARCHAR(20) | NOT NULL | 측정 기준: CURRENT_PRICE / AVG_PRICE / HIGHEST_PRICE / AI_SCORE |
| compare_type | VARCHAR(10) | NOT NULL | ABOVE / BELOW |
| target_value | DECIMAL(18,4) | NOT NULL | 목표값(절대가격 또는 %). `is_rate=TRUE`면 % |
| is_rate | BOOLEAN | DEFAULT FALSE | `target_value`가 %인지 여부 |
| trailing_highest | DECIMAL(18,4) | NULL | 트레일링 스탑의 추적 고점 (TRAILING_STOP 전용) |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 생성 일시 |

```sql
CREATE TABLE condition_triggers (
    trigger_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    condition_id     BIGINT NOT NULL,
    trigger_type     VARCHAR(20) NOT NULL COMMENT 'PRICE, PROFIT_TARGET, STOP_LOSS, TRAILING_STOP, AI_SCORE',
    base_type        VARCHAR(20) NOT NULL COMMENT 'CURRENT_PRICE, AVG_PRICE, HIGHEST_PRICE, AI_SCORE',
    compare_type     VARCHAR(10) NOT NULL COMMENT 'ABOVE, BELOW',
    target_value     DECIMAL(18,4) NOT NULL,
    is_rate          BOOLEAN DEFAULT FALSE COMMENT 'true면 target_value는 %',
    trailing_highest DECIMAL(18,4) NULL COMMENT '트레일링 스탑 추적 고점',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (condition_id) REFERENCES trading_conditions(condition_id) ON DELETE CASCADE,
    CONSTRAINT chk_triggers_trigger_type CHECK (trigger_type IN ('PRICE','PROFIT_TARGET','STOP_LOSS','TRAILING_STOP','AI_SCORE')),
    CONSTRAINT chk_triggers_base_type CHECK (base_type IN ('CURRENT_PRICE','AVG_PRICE','HIGHEST_PRICE','AI_SCORE')),
    CONSTRAINT chk_triggers_compare_type CHECK (compare_type IN ('ABOVE','BELOW')),
    CONSTRAINT chk_triggers_trailing CHECK (
        (trigger_type = 'TRAILING_STOP' AND trailing_highest IS NOT NULL) OR
        (trigger_type <> 'TRAILING_STOP')
    )
);
```

---

### ⑥ 매매/체결 히스토리 테이블 (trading_histories)

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

---

### ⑦ AI 뉴스 감성 분석 및 RAG 테이블 (news_sentiments)

```sql
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
```

---

### ⑧ AI 투자 맞춤 리포트 테이블 (ai_investment_reports) — v6 정정

> **v6 정정**: `GET /reports/stocks/{stockCode}`(api.md §5.1)가 종목별 최신 리포트를 조회해야 하므로,
> 실제 구현(및 `AiInvestmentReport` 엔티티)에는 `stock_code` 컬럼과 `stocks` FK가 처음부터 존재했다.
> 이 문서의 DDL에 누락되어 있던 것을 실제 스키마 기준으로 정정한다(기능 변경 없음, 문서만 갱신).

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| report_id | BIGINT | PK, AUTO_INCREMENT | 리포트 식별 ID |
| user_id | BIGINT | FK(users.user_id), NOT NULL | 리포트 대상 유저 |
| stock_code | VARCHAR(10) | FK(stocks.stock_code), NOT NULL | 리포트 대상 종목 |
| report_content | TEXT | NOT NULL | 리포트 본문(JSON 문자열) |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 생성 일시 |

```sql
CREATE TABLE ai_investment_reports (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(10) NOT NULL,
    report_content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_investment_reports_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_ai_investment_reports_stock_code FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);
```

> **v7 정정**: 운영 DB의 자동생성 FK명(`ai_investment_reports_ibfk_1`, `FKh0vpbhfnukl9fxc1wx8pssc4w`)을
> 위 이름으로 마이그레이션 완료(`ALTER TABLE ... DROP FOREIGN KEY ... / ADD CONSTRAINT ...`).
> 이제 문서와 실제 스키마가 일치한다.

---

## 3. 전체 DDL 통합본

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

-- 2. 자산 관련
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

-- 3. 매매 조건(액션) 및 발동 조건(트리거)
CREATE TABLE trading_conditions (
    condition_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    stock_code       VARCHAR(10) NOT NULL,
    order_type       VARCHAR(10) NOT NULL COMMENT 'BUY, SELL',
    order_quantity   INT NOT NULL,
    order_price_type VARCHAR(10) NOT NULL DEFAULT 'MARKET' COMMENT 'MARKET, LIMIT',
    limit_price      DECIMAL(18,4) NULL,
    condition_logic  VARCHAR(3) NOT NULL DEFAULT 'AND' COMMENT 'AND, OR',
    execution_mode   VARCHAR(10) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO, MANUAL',
    is_active        BOOLEAN DEFAULT TRUE,
    is_persistent    BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'TRUE면 실행 후에도 반복 감시 유지',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (stock_code) REFERENCES stocks(stock_code),
    CONSTRAINT chk_conditions_order_type CHECK (order_type IN ('BUY','SELL')),
    CONSTRAINT chk_conditions_order_price_type CHECK (order_price_type IN ('MARKET','LIMIT')),
    CONSTRAINT chk_conditions_logic CHECK (condition_logic IN ('AND','OR')),
    CONSTRAINT chk_conditions_execution_mode CHECK (execution_mode IN ('AUTO','MANUAL')),
    CONSTRAINT chk_conditions_limit_price CHECK (
        (order_price_type = 'LIMIT' AND limit_price IS NOT NULL) OR
        (order_price_type = 'MARKET' AND limit_price IS NULL)
    )
);

CREATE TABLE condition_triggers (
    trigger_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    condition_id     BIGINT NOT NULL,
    trigger_type     VARCHAR(20) NOT NULL COMMENT 'PRICE, PROFIT_TARGET, STOP_LOSS, TRAILING_STOP, AI_SCORE',
    base_type        VARCHAR(20) NOT NULL COMMENT 'CURRENT_PRICE, AVG_PRICE, HIGHEST_PRICE, AI_SCORE',
    compare_type     VARCHAR(10) NOT NULL COMMENT 'ABOVE, BELOW',
    target_value     DECIMAL(18,4) NOT NULL,
    is_rate          BOOLEAN DEFAULT FALSE COMMENT 'true면 target_value는 %',
    trailing_highest DECIMAL(18,4) NULL COMMENT '트레일링 스탑 추적 고점',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (condition_id) REFERENCES trading_conditions(condition_id) ON DELETE CASCADE,
    CONSTRAINT chk_triggers_trigger_type CHECK (trigger_type IN ('PRICE','PROFIT_TARGET','STOP_LOSS','TRAILING_STOP','AI_SCORE')),
    CONSTRAINT chk_triggers_base_type CHECK (base_type IN ('CURRENT_PRICE','AVG_PRICE','HIGHEST_PRICE','AI_SCORE')),
    CONSTRAINT chk_triggers_compare_type CHECK (compare_type IN ('ABOVE','BELOW')),
    CONSTRAINT chk_triggers_trailing CHECK (
        (trigger_type = 'TRAILING_STOP' AND trailing_highest IS NOT NULL) OR
        (trigger_type <> 'TRAILING_STOP')
    )
);

CREATE INDEX idx_conditions_user_active ON trading_conditions(user_id, is_active);
CREATE INDEX idx_triggers_condition ON condition_triggers(condition_id);

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
    stock_code VARCHAR(10) NOT NULL,
    report_content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_investment_reports_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_ai_investment_reports_stock_code FOREIGN KEY (stock_code) REFERENCES stocks(stock_code)
);
```
