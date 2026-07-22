import apiClient from './client'
import type { ApiResponse } from '@/types/api'
import type { ChartResponse } from '@/types/chart'

export const getDailyChart = (
  stockCode: string,
  period: string = 'D'
): Promise<ApiResponse<ChartResponse>> =>
  apiClient.get(`/charts/${stockCode}/daily?period=${period}`).then(r => r.data)

export const getMinuteChart = (
  stockCode: string
): Promise<ApiResponse<ChartResponse>> =>
  apiClient.get(`/charts/${stockCode}/minute`).then(r => r.data)