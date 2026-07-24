# 실시간 AI 모의투자 시스템 — 프론트엔드 설계 문서 (v1)

> Next.js App Router + TypeScript + Zustand + React Query 기반 프론트엔드 전체 설계를 정의합니다.
> API 명세서 v1, 사용자 워크플로우 v1, Redis 데이터 설계 v1, 로그인 방식(JWT) 논의 내용을 기반으로 작성되었습니다.

---

## 1. 기술 스택

| 항목 | 기술 | 이유 |
|---|---|---|
| 프레임워크 | Next.js 14 (App Router) | 서버/클라이언트 컴포넌트 분리, middleware 지원 |
| 언어 | TypeScript | API 응답 타입 고정으로 백엔드 변경 시 즉시 감지 |
| 스타일 | Tailwind CSS | 빠른 UI 구성 |
| 전역 상태 | Zustand | 클라이언트 전용 상태 (토큰, WS 실시간 데이터) |
| 서버 상태 | React Query (TanStack Query) | API 데이터 fetch/캐싱/재시도 |
| HTTP 클라이언트 | Axios | 인터셉터 기반 401 자동 refresh |
| 실시간 통신 | WebSocket (네이티브) | KIS 시세, 자동매매 알림, 리포트 완료 알림 |

---

## 2. 디렉토리 구조

```
front/src/
├── app/
│   ├── (auth)/                        ← 공개 그룹 (미들웨어 통과)
│   │   ├── login/
│   │   │   └── page.tsx               ← 카카오 로그인 버튼
│   │   └── callback/
│   │       └── kakao/
│   │           └── page.tsx           ← code 수신 → POST /auth/login → /dashboard
│   ├── (main)/                        ← 보호 그룹 (refreshToken 쿠키 필수)
│   │   ├── layout.tsx                 ← AuthProvider + 네비게이션 공통 레이아웃
│   │   ├── dashboard/
│   │   │   └── page.tsx               ← 자산현황 + 대장주 10종목 + WS 실시간
│   │   ├── conditions/
│   │   │   └── page.tsx               ← 자동매매 조건 관리 + 매매이력
│   │   └── reports/
│   │       └── [stockCode]/
│   │           └── page.tsx           ← AI 리포트 조회/새로고침
│   ├── layout.tsx                     ← 루트 레이아웃 (AuthProvider 래핑)
│   └── page.tsx                       ← / → /dashboard 리다이렉트
│
├── components/
│   ├── common/                        ← 버튼, 모달, 토스트, 로딩 스피너
│   ├── auth/
│   │   └── AuthProvider.tsx           ← 탭 재접속 시 accessToken 자동 복구
│   ├── assets/                        ← 자산 카드, 보유종목 테이블, 종목 카드
│   ├── conditions/                    ← 조건 등록 폼, 조건 목록, 매매이력 테이블
│   └── reports/                       ← 리포트 JSON 뷰어 (점수 배지, 감성 태그, 생성일시, AI 경고문, 다국어)
│   └── dashboard/
│       ├── CandleChart.tsx            ← 캔들 차트
│       ├── NewsTicker.tsx             ← 뉴스 티커
│       ├── StockSidebar.tsx           ← 종목 사이드바
│       ├── TradingPanel.tsx           ← 매수/매도 패널 (시장가/지정가, 수량, 비율 버튼)
│       └── ExecutionList.tsx          ← 실시간 체결 내역 테이블 (체결가/체결량(매수:빨강/매도:파랑)/등락률/누적거래량/시간)
│
├── lib/
│   ├── api/
│   │   ├── client.ts                  ← Axios 인스턴스 + 401 인터셉터
│   │   ├── auth.ts                    ← login / logout
│   │   ├── assets.ts                  ← getAssets / createOrder / getHistories
│   │   ├── conditions.ts              ← getConditions / createCondition / deleteCondition
│   │   └── reports.ts                 ← getReport / refreshReport
│   ├── socket/
│   │   ├── socketClient.ts            ← WebSocket 연결·재연결·이벤트 핸들링
│   │   └── socketEvents.ts            ← 이벤트 타입 상수
│   ├── store/
│   │   ├── authStore.ts               ← accessToken + user (Zustand)
│   │   └── assetStore.ts              ← 자산 현황 + WS 실시간 갱신 (Zustand)
│   ├── hooks/
│   │   ├── useAuth.ts                 ← 로그인 상태 + 카카오 로그인 + 로그아웃
│   │   ├── useWebSocket.ts            ← WS 연결 훅 (컴포넌트에서 사용)
│   │   └── useAssets.ts               ← 자산 조회 훅 (React Query + assetStore 연동)
│   └── utils/
│       └── constants.ts               ← API URL, WS URL, 카카오 설정 등
│
├── types/
│   ├── api.ts                         ← ApiResponse<T>
│   ├── auth.ts                        ← LoginResponse, RefreshResponse, UserInfo
│   ├── assets.ts                      ← WalletDto, HoldingDto, OrderDto, HistoryDto
│   ├── conditions.ts                  ← TradingConditionDto, ConditionRequest
│   ├── reports.ts                     ← ReportDto
│   └── socket.ts                      ← WS 이벤트 페이로드 타입
│
└── middleware.ts                      ← 보호 라우트 가드 (refreshToken 쿠키 판별)
```

---

## 3. 상태관리 설계 — Zustand

### 3.1 역할 분리 원칙

Zustand와 React Query는 경쟁 관계가 아니라 역할이 다르다.

| 상태 종류 | 담당 | 기준 |
|---|---|---|
| 서버 데이터 (목록, 리포트, 조건 등) | **React Query** | 서버에서 fetch해오는 모든 데이터 |
| 클라이언트 전용 상태 | **Zustand** | 서버 요청 없이 클라이언트에서만 존재하는 데이터 |

Zustand를 새로 추가하고 싶은 상황이 생기면, 먼저 **"이게 React Query로 해결 안 되나?"** 를 확인한다.
대부분의 서버 데이터는 React Query가 더 적합하다. **클라이언트 전용 실시간 상태(KIS WebSocket 시세, WebSocket 알림 등)에 한해 추가를 허용한다.**
Store 개수를 늘릴 때는 반드시 설계 문서에 기록하고 사유를 명시한다.

---

### 3.2 `authStore` — 인증 상태

```typescript
// lib/store/authStore.ts
import { create } from 'zustand'

interface UserInfo {
  userId: number
  nickname: string
  email: string
}

interface AuthState {
  accessToken: string | null
  user: UserInfo | null
  isLoggedIn: boolean

  setAuth: (token: string, user: UserInfo) => void
  clearAuth: () => void
}

const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  isLoggedIn: false,

  setAuth: (token, user) => set({ accessToken: token, user, isLoggedIn: true }),
  clearAuth: () => set({ accessToken: null, user: null, isLoggedIn: false }),
}))
```

**`setAuth` 호출 시점**

| 시점 | 호출 주체 |
|---|---|
| 카카오 로그인 성공 | `app/(auth)/callback/kakao/page.tsx` |
| 탭 재접속 후 refresh 성공 | `components/auth/AuthProvider.tsx` |
| 401 응답 후 자동 refresh 성공 | `lib/api/client.ts` (Axios 인터셉터) |

**`clearAuth` 호출 시점**

| 시점 | 호출 주체 |
|---|---|
| 로그아웃 버튼 클릭 | `lib/hooks/useAuth.ts` |
| refresh 실패 (E4011) | `lib/api/client.ts` (Axios 인터셉터) |

**읽는 곳**

| 위치 | 읽는 값 | 용도 |
|---|---|---|
| `lib/api/client.ts` | `accessToken` | `Authorization: Bearer` 헤더 자동 주입 |
| `components/auth/AuthProvider.tsx` | `accessToken` | null이면 `/auth/refresh` 자동 호출 |
| 네비게이션 바 | `user.nickname`, `isLoggedIn` | 로그인 상태 및 닉네임 표시 |
| 각 보호 페이지 | `isLoggedIn` | 조건부 렌더링 |

---

### 3.3 `assetStore` — 자산 현황

```typescript
// lib/store/assetStore.ts
import { create } from 'zustand'

interface HoldingDto {
  stockCode: string
  stockName: string
  quantity: number
  averagePrice: number
  currentPrice: number
  evaluationAmount: number
  profitLossRate: number
}

interface AssetState {
  walletBalance: number | null
  holdings: HoldingDto[]
  totalEvaluationAmount: number | null
  totalProfitLossRate: number | null
  isLoading: boolean

  setAssets: (data: AssetSummaryResponse) => void
  updateHoldingPrice: (stockCode: string, currentPrice: number) => void
  applyOrderFilled: (history: HistoryDto) => void
}

const useAssetStore = create<AssetState>((set, get) => ({
  walletBalance: null,
  holdings: [],
  totalEvaluationAmount: null,
  totalProfitLossRate: null,
  isLoading: false,

  setAssets: (data) => set({
    walletBalance: data.walletBalance,
    holdings: data.holdings,
    totalEvaluationAmount: data.totalEvaluationAmount,
    totalProfitLossRate: data.totalProfitLossRate,
  }),

  updateHoldingPrice: (stockCode, currentPrice) => set((state) => ({
    holdings: state.holdings.map(h =>
      h.stockCode === stockCode
        ? { ...h, currentPrice, evaluationAmount: h.quantity * currentPrice }
        : h
    ),
  })),

  applyOrderFilled: (history) => {
    // 체결 완료 시 잔고/보유종목 즉시 반영 (다음 GET /assets 전까지 낙관적 업데이트)
  },
}))
```

**`assetStore`가 핵심인 이유:**

WebSocket 리스너는 컴포넌트 바깥(`socketClient.ts`)에 위치한다.
컴포넌트 바깥에서 컴포넌트 상태를 변경하려면 Zustand store를 거치는 것이 유일한 방법이다.

```
KIS Websocket 시세 push
        │
        ▼
socketClient.ts (컴포넌트 바깥)
        │  useAssetStore.getState().updateHoldingPrice(stockCode, price)
        ▼
assetStore 상태 변경
        │  구독 중인 컴포넌트만 리렌더링
        ▼
/dashboard 보유종목 테이블 실시간 갱신
```

**액션 호출 시점**

| 액션 | 호출 시점 | 호출 주체 |
|---|---|---|
| `setAssets` | `GET /assets` 응답 수신 | `lib/hooks/useAssets.ts` |
| `updateHoldingPrice` | WS `PRICE_ALERT` 이벤트 | `lib/socket/socketClient.ts` |
| `applyOrderFilled` | WS `ORDER_FILLED` 이벤트 | `lib/socket/socketClient.ts` |

---

## 4. 인증 흐름

### 4.1 최초 로그인

```
/login 페이지 → "카카오로 시작하기" 클릭
→ 카카오 OAuth 인가 URL로 리다이렉트
→ 동의 → /callback/kakao?code=xxx
→ POST /api/v1/auth/login { provider: "KAKAO", code }
→ 응답: { accessToken, user } + Set-Cookie: refreshToken (HttpOnly)
→ authStore.setAuth(accessToken, user)
→ /dashboard 이동
```

### 4.2 탭 재접속 (accessToken 복구)

```
브라우저 재접속
→ middleware.ts: refreshToken 쿠키 있음? → 없으면 /login
→ AuthProvider 마운트: authStore.accessToken === null?
  → 없으면: POST /api/v1/auth/refresh (쿠키 자동 첨부)
    → 성공: authStore.setAuth(newToken, user) → children 렌더링
    → 실패: children 렌더링 안 함 → middleware가 /login 처리
  → 있으면: 바로 children 렌더링
```

### 4.3 Access Token 만료 (401 자동 처리)

```
API 요청 → 401 수신
→ Axios 인터셉터: POST /api/v1/auth/refresh 자동 호출
  → 성공: authStore.setAuth(newToken) → 원래 요청 재시도
  → 실패: authStore.clearAuth() → /login 리다이렉트
※ refresh 요청 자체가 401인 경우 재시도하지 않음 (무한루프 방지 필수)
```

### 4.4 로그아웃

```
로그아웃 버튼
→ POST /api/v1/auth/logout (Authorization: Bearer accessToken)
→ authStore.clearAuth()
→ Set-Cookie: refreshToken=; Max-Age=0 (쿠키 제거)
→ /login 이동
```

---

## 5. WebSocket 설계

### 5.1 이벤트 타입 상수 (`lib/socket/socketEvents.ts`)

```typescript
export const SOCKET_EVENTS = {
  PRICE_ALERT: 'PRICE_ALERT',
  AI_SCORE_ALERT: 'AI_SCORE_ALERT',
  ORDER_FILLED: 'ORDER_FILLED',
  ORDER_FAILED: 'ORDER_FAILED',
  REPORT_READY: 'REPORT_READY',
  EXECUTION: 'EXECUTION',
} as const
```

### 5.2 이벤트별 처리

| 이벤트 | payload | 처리 |
|---|---|---|
| `PRICE_ALERT` | `{ conditionId, stockCode, currentPrice }` | 토스트 알림 표시 |
| `AI_SCORE_ALERT` | `{ conditionId, stockCode, aiScore }` | 토스트 알림 표시 |
| `ORDER_FILLED` | `{ historyId, stockCode, executionPrice, executionQuantity }` | 토스트 + `assetStore.applyOrderFilled()` |
| `ORDER_FAILED` | `{ historyId, stockCode, failureReason }` | 토스트 알림 표시 |
| `REPORT_READY` | `{ stockCode, reportId }` | 해당 stockCode 리포트 페이지 자동 재조회 |
| `EXECUTION` | `{ stockCode, price, volume, changeRate, accumulatedVolume, time }` | executionStore.pushExecution() → ExecutionList 실시간 갱신 |

### 5.3 연결 규칙

- 연결: `wss://{host}/ws?token={accessToken}` — 로그인 후 `(main)/layout.tsx` 마운트 시점에 연결
- 연결 끊김 시: 자동 재연결 로직 포함 (지수 백오프 권장)
- 로그아웃 시: 연결 명시적 종료

---

## 6. 타입 정의

### `types/api.ts`
```typescript
export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T | null
}
```

### `types/auth.ts`
```typescript
export interface UserInfo {
  userId: number
  nickname: string
  email: string
}
export interface LoginResponse {
  accessToken: string
  accessTokenExpiresIn: number
  isNewUser: boolean
  user: UserInfo
}
export interface RefreshResponse {
  accessToken: string
  accessTokenExpiresIn: number
  user: UserInfo
}
```

### `types/assets.ts`
```typescript
export interface HoldingDto {
  stockCode: string
  stockName: string
  quantity: number
  averagePrice: number
  currentPrice: number
  evaluationAmount: number
  profitLossRate: number
}
export interface AssetSummaryResponse {
  walletBalance: number
  totalEvaluationAmount: number
  totalProfitLossAmount: number
  totalProfitLossRate: number
  holdings: HoldingDto[]
}
export interface HistoryDto {
  historyId: number
  stockCode: string
  orderType: 'BUY' | 'SELL'
  status: 'PENDING' | 'FILLED' | 'FAILED'
  executionPrice: number | null
  executionQuantity: number | null
  totalAmount: number | null
  failureReason: string | null
  requestedAt: string
  executedAt: string | null
}
```

### `types/conditions.ts`
```typescript
export interface TradingConditionDto {
  conditionId: number
  stockCode: string
  targetPrice: number | null
  priceConditionType: 'ABOVE' | 'BELOW' | null
  targetAiScore: number | null
  aiConditionType: 'ABOVE' | 'BELOW' | null
  conditionLogic: 'AND' | 'OR'
  orderType: 'BUY' | 'SELL'
  orderQuantity: number
  isActive: boolean
  createdAt: string
}
export interface ConditionRequest {
  stockCode: string
  targetPrice?: number
  priceConditionType?: 'ABOVE' | 'BELOW'
  targetAiScore?: number
  aiConditionType?: 'ABOVE' | 'BELOW'
  conditionLogic: 'AND' | 'OR'
  orderType: 'BUY' | 'SELL'
  orderQuantity: number
}
```

### `types/socket.ts`
```typescript
export interface WsMessage<T = unknown> {
  type: string
  payload: T
}
export interface PriceAlertPayload {
  conditionId: number
  stockCode: string
  currentPrice: number
}
export interface OrderFilledPayload {
  historyId: number
  stockCode: string
  executionPrice: number
  executionQuantity: number
}
export interface ReportReadyPayload {
  stockCode: string
  reportId: number
}
```

---

## 7. 페이지별 API 매핑

| 페이지 | 진입 시 호출 | 사용자 액션 시 호출 | WS 이벤트 처리 |
|---|---|---|---|
| `/login` | - | OAuth 리다이렉트 | - |
| `/callback/kakao` | POST /auth/login | - | - |
| `/dashboard` | GET /assets | POST /assets/orders | PRICE_ALERT, ORDER_FILLED |
| `/conditions` | GET /conditions, GET /assets/histories | POST /conditions, DELETE /conditions/{id} | ORDER_FILLED, ORDER_FAILED |
| `/reports/[code]` | GET /reports/stocks/{code} | POST /reports/stocks/{code}/refresh | REPORT_READY |
| 전역(main layout) | POST /auth/refresh (AuthProvider) | POST /auth/logout | 모든 이벤트 수신 대기 |

---

## 8. 환경변수

```bash
# .env.local
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8080
NEXT_PUBLIC_KAKAO_CLIENT_ID={REST API 키}
NEXT_PUBLIC_KAKAO_REDIRECT_URI=http://localhost:3000/callback/kakao
```

---

## 9. 다국어 처리 (i18n) — v4 신규

### 9.1 기본 원칙

모든 사용자 노출 텍스트는 `t()` 함수를 통해 i18n 키로 렌더링한다. 하드코딩 문자열 금지.

### 9.2 파일 구조

```
src/lib/i18n/
├── config.ts          ← i18next 초기화
└── locales/
    ├── ko.json        ← 한국어 번역
    └── en.json        ← 영어 번역
```

### 9.3 언어 전환

- `languageStore.ts` (Zustand)가 언어 상태 관리
- `localStorage`의 `i18nextLng` 키에 영구 저장
- 언어 전환 버튼은 Header 컴포넌트에 위치

### 9.4 구현 규칙

- `'use client'` 컴포넌트에서 `useTranslation()` 훅 사용
- Suspense fallback 텍스트도 i18n 키 사용
- 신규 페이지 추가 시 ko.json / en.json 키 누락 확인
- 언어별 다른 JSX 구조(조건부 렌더링) 금지

---

## 10. 절대 하지 말 것

- `accessToken`을 `localStorage`, `sessionStorage`, 쿠키에 저장하는 코드 작성 금지
- `refreshToken`을 JS 코드에서 읽거나 저장하는 코드 작성 금지
- Zustand store를 사유 없이 무분별하게 늘리는 것 금지 — 추가 시 설계 문서에 기록하고 클라이언트 전용 실시간 상태(WebSocket 시세 등)인지 확인
- WebSocket 이벤트 수신 시 컴포넌트 상태를 직접 변경하는 코드 작성 금지 (반드시 store 액션 경유)
- `types/` 외부에 인라인 타입 정의 금지
- `middleware.ts`에서 `accessToken`(메모리)으로 인증 판별 시도 금지 — 서버사이드에서 접근 불가