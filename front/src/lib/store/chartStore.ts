import { create } from 'zustand'

interface ChartState {
  prices: Record<string, number>       // stockCode → 현재가
  updatePrice: (stockCode: string, price: number) => void
}

export const useChartStore = create<ChartState>((set) => ({
  prices: {},
  updatePrice: (stockCode, price) =>
    set((state) => ({
      prices: { ...state.prices, [stockCode]: price },
    })),
}))