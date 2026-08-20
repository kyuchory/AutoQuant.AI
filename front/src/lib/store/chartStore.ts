import { create } from 'zustand'
import type { PriceData } from '@/types/chart'

interface ChartState {
  prices: Record<string, PriceData>       // stockCode → 현재가+전일대비 등락률+체결량
  updatePrice: (stockCode: string, price: number, changeRate: number, volume?: number) => void
  connectionStatus: 'connected' | 'disconnected'
  setConnectionStatus: (status: 'connected' | 'disconnected') => void
}

export const useChartStore = create<ChartState>((set) => ({
  prices: {},
  updatePrice: (stockCode, price, changeRate, volume = 0) =>
    set((state) => ({
      prices: { ...state.prices, [stockCode]: { price, changeRate, volume } },
    })),
  connectionStatus: 'connected',
  setConnectionStatus: (status) => set({ connectionStatus: status }),
}))
