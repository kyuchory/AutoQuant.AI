/** 모니터링 대상 종목 정보 (GET /stocks 응답) */
export interface StockInfo {
  stockCode: string
  stockName: string
  currentPrice: number
  changeRate: number
}