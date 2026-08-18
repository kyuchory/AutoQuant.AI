export interface HoldingInfo {
  stockCode: string
  stockName: string
  quantity: number
  averagePrice: number
  currentPrice: number
  evaluationAmount: number
  profitLossAmount: number
  profitLossRate: number
}

export interface AssetSummaryResponse {
  walletBalance: number
  totalEvaluationAmount: number
  totalProfitLossAmount: number
  totalProfitLossRate: number
  holdings: HoldingInfo[]
}

export interface OrderRequest {
  stockCode: string
  orderType: 'BUY' | 'SELL'
  quantity: number
  ordDvsn?: string
  price?: number
}

export interface OrderResponse {
  historyId: number
  stockCode: string
  orderType: string
  status: 'PENDING' | 'FILLED' | 'FAILED'
  executionPrice: number | null
  executionQuantity: number | null
  totalAmount: number | null
  failureReason: string | null
  requestedAt: string
  executedAt: string | null
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