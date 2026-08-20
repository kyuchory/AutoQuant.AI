export type PeriodCode = 'MINUTE' | 'D' | 'W' | 'M' | 'Y'

/** chartStore.prices의 종목별 실시간 시세 (PRICE_TICK 수신값) */
export interface PriceData {
  price: number
  changeRate: number
  volume: number
}

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