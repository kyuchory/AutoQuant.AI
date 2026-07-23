/** AI 투자 리포트 응답 타입 */
export interface ReportDto {
  reportId: number | null
  stockCode: string
  stockName: string
  reportContent: string
  createdAt: string | null
  cacheHit: boolean
}

/** 리포트 새로고침 응답 타입 */
export interface RefreshResponse {
  requestId: string
  status: 'ACCEPTED'
}