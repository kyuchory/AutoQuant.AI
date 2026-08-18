import apiClient from './client'
import type { ApiResponse } from '@/types/api'
import type { AssetSummaryResponse, OrderRequest, OrderResponse, HistoryDto } from '@/types/assets'

/** GET /api/v1/assets — 자산 종합 조회 */
export function getAssets(): Promise<ApiResponse<AssetSummaryResponse>> {
  return apiClient.get('/assets').then((r) => r.data)
}

/** POST /api/v1/assets/orders — 수동 매매 주문 */
export function createOrder(request: OrderRequest): Promise<ApiResponse<OrderResponse>> {
  return apiClient.post('/assets/orders', request).then((r) => r.data)
}

/** GET /api/v1/assets/histories — 매매 체결 이력 조회 */
export function getHistories(params?: {
  page?: number
  size?: number
  stockCode?: string
  status?: string
}): Promise<ApiResponse<{ content: HistoryDto[]; page: number; size: number; totalElements: number; totalPages: number }>> {
  return apiClient.get('/assets/histories', { params }).then((r) => r.data)
}
