/** 전체 종목 실시간 시세 브로드캐스트 (조건과 무관, KIS WebSocket 매 틱) */
export interface PriceTickPayload {
  stockCode: string
  currentPrice: number
  changeRate: number
  volume: number
}

/** 가격 조건 충족 시 1회성 알림 (ConditionMatchingEngine 발행, api.md §6.2) */
export interface PriceAlertPayload {
  conditionId: number
  stockCode: string
  currentPrice: number
}

export interface OrderFilledPayload {
  historyId: number
  stockCode: string
  orderType?: 'BUY' | 'SELL'
  executionPrice: number
  executionQuantity: number
}

export interface OrderFailedPayload {
  historyId: number
  stockCode: string
  failureReason: string
}

export interface ReportReadyPayload {
  stockCode: string
  reportId: number
}

export interface ExecutionPayload {
  stockCode: string
  price: number
  volume: number
  changeRate: number
  accumulatedVolume: number
  time: string
  sign: string // KIS CCLD_DVSN(체결구분, docs/kisflow.md §4): "1"=매수(빨강), "5"=매도(파랑)
}

/** 반자동 매매 제안 페이로드 (ORDER_PROPOSAL) */
export interface OrderProposalPayload {
  conditionId: number
  stockCode: string
  stockName: string
  orderType: 'BUY' | 'SELL'
  orderQuantity: number
  orderPriceType: 'MARKET' | 'LIMIT'
  limitPrice?: number | null
  currentPrice: number
  triggerReason: string

  // AI 분석 근거 및 기사 정보
  aiScore?: number
  aiSentiment?: 'GOOD' | 'BAD' | 'NEUTRAL'
  aiReason?: string
  newsTitle?: string
  newsUrl?: string
  newsSummary?: string
  newsPublishedAt?: string
}
