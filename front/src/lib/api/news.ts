import apiClient from './client'
import type { ApiResponse } from '@/types/api'
import type { NewsTickerDto } from '@/types/news'

/** 대시보드 뉴스 티커 조회 */
export async function getNewsTicker(): Promise<NewsTickerDto[]> {
  const response = await apiClient.get<ApiResponse<NewsTickerDto[]>>('/news/ticker')
  return response.data.data ?? []
}