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

  setAssets: (data: {
    walletBalance: number
    holdings: HoldingDto[]
    totalEvaluationAmount: number
    totalProfitLossRate: number
  }) => void
  updateHoldingPrice: (stockCode: string, currentPrice: number) => void
  applyOrderFilled: (history: {
    historyId: number
    stockCode: string
    executionPrice: number
    executionQuantity: number
  }) => void
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

  applyOrderFilled: () => {
    // 체결 완료 처리 (추후 구현)
  },
}))