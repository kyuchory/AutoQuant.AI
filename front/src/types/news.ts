/** 대시보드 뉴스 티커 API 응답 타입 */
export interface NewsTickerDto {
  stockCode: string
  stockName: string
  title: string
  newsUrl: string
  sentiment: 'GOOD' | 'BAD' | 'NEUTRAL'
  aiScore: number
  publishedAt: string
}
