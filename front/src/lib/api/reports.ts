import apiClient from './client'
import type { ApiResponse } from '@/types/api'
import type { ReportDto, RefreshResponse } from '@/types/reports'

/** 특정 종목의 AI 리포트 조회 */
export async function getReport(stockCode: string): Promise<ReportDto | null> {
  const response = await apiClient.get<ApiResponse<ReportDto>>(`/reports/stocks/${stockCode}`)
  return response.data.data
}

/** 리포트 새로고침 요청 (RabbitMQ 비동기) */
export async function refreshReport(stockCode: string): Promise<RefreshResponse | null> {
  const response = await apiClient.post<ApiResponse<RefreshResponse>>(`/reports/stocks/${stockCode}/refresh`)
  return response.data.data
}