export type PeriodCode = 'MINUTE' | 'D' | 'W' | 'M' | 'Y'

export interface CandleItem {
  time: number
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface ChartResponse {
  stockCode: string
  stockName: string
  periodCode: PeriodCode
  currentPrice: number
  changeAmount: number
  changeRate: number
  openPrice: number
  highPrice: number
  lowPrice: number
  candles: CandleItem[]
}