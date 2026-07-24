import { create } from 'zustand'

interface PriceData {
  price: number
  changeRate: number
}

interface ChartState {
  prices: Record<string, PriceData>       // stockCode → 현재가+전일대비 등락률
  updatePrice: (stockCode: string, price: number, changeRate: number) => void
}

export const useChartStore = create<ChartState>((set) => ({
  prices: {},
  updatePrice: (stockCode, price, changeRate) =>
    set((state) => ({
      prices: { ...state.prices, [stockCode]: { price, changeRate } },
    })),
}))
