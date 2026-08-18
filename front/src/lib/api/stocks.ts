import apiClient from './client'
import type { ApiResponse } from '@/types/api'
import type { StockInfo } from '@/types/stocks'

/** 모니터링 대상 종목 + Redis 현재가 + 전일대비 등락률 조회 */
export async function getStocks(): Promise<StockInfo[]> {
  const { data } = await apiClient.get<ApiResponse<StockInfo[]>>('/stocks')
  return data.data ?? []
}