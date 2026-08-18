import { create } from 'zustand'
import type { OrderFilledPayload } from '@/types/socket'

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

  setAssets: (data: {
    walletBalance: number
    holdings: HoldingDto[]
    totalEvaluationAmount: number
    totalProfitLossRate: number
  }) => void
  updateHoldingPrice: (stockCode: string, currentPrice: number) => void
  applyOrderFilled: (history: OrderFilledPayload) => void
}

export const useAssetStore = create<AssetState>((set) => ({
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

  // 체결 완료 시 잔고/보유종목 즉시 반영 (다음 GET /assets 전까지의 낙관적 업데이트).
  // orderType이 없거나 walletBalance가 아직 로드되지 않았으면(GET /assets 전) 건드리지 않는다.
  applyOrderFilled: (history) => set((state) => {
    if (!history.orderType || state.walletBalance == null) return {}

    const amount = history.executionPrice * history.executionQuantity
    const idx = state.holdings.findIndex((h) => h.stockCode === history.stockCode)

    if (history.orderType === 'SELL') {
      if (idx === -1) return { walletBalance: state.walletBalance + amount }
      const holding = state.holdings[idx]
      const remaining = holding.quantity - history.executionQuantity
      const holdings = remaining > 0
        ? state.holdings.map((h, i) => i === idx
            ? { ...h, quantity: remaining, currentPrice: history.executionPrice, evaluationAmount: remaining * history.executionPrice }
            : h)
        : state.holdings.filter((_, i) => i !== idx)
      return { walletBalance: state.walletBalance + amount, holdings }
    }

    // BUY
    if (idx === -1) {
      // 신규 매수 종목은 종목명 등 서버 계산 필드가 없어 다음 GET /assets 갱신에서 채워진다.
      return { walletBalance: state.walletBalance - amount }
    }
    const holding = state.holdings[idx]
    const totalQuantity = holding.quantity + history.executionQuantity
    const averagePrice = (holding.averagePrice * holding.quantity + amount) / totalQuantity
    const holdings = state.holdings.map((h, i) => i === idx
      ? { ...h, quantity: totalQuantity, averagePrice, currentPrice: history.executionPrice, evaluationAmount: totalQuantity * history.executionPrice }
      : h)
    return { walletBalance: state.walletBalance - amount, holdings }
  }),
}))