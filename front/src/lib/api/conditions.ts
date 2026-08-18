import apiClient from './client'
import type { ApiResponse } from '@/types/api'
import type { TradingConditionRequest, TradingConditionResponse } from '@/types/conditions'

/** POST /api/v1/conditions — 자동 매매 조건 등록 */
export function createCondition(
  request: TradingConditionRequest
): Promise<ApiResponse<TradingConditionResponse>> {
  return apiClient.post('/conditions', request).then((r) => r.data)
}

/** GET /api/v1/conditions — 조건 목록 조회 */
export function getConditions(): Promise<ApiResponse<TradingConditionResponse[]>> {
  return apiClient.get('/conditions').then((r) => r.data)
}

/** PATCH /api/v1/conditions/{conditionId}/active — 조건 감시 ON/OFF 토글 */
export function updateConditionActive(
  conditionId: number,
  isActive: boolean
): Promise<ApiResponse<TradingConditionResponse>> {
  return apiClient.patch(`/conditions/${conditionId}/active`, { isActive }).then((r) => r.data)
}

/** PUT /api/v1/conditions/{conditionId} — 조건 수정 */
export function updateCondition(
  conditionId: number,
  request: TradingConditionRequest
): Promise<ApiResponse<TradingConditionResponse>> {
  return apiClient.put(`/conditions/${conditionId}`, request).then((r) => r.data)
}

/** DELETE /api/v1/conditions/{conditionId} — 조건 삭제 */
export function deleteCondition(conditionId: number): Promise<ApiResponse<null>> {
  return apiClient.delete(`/conditions/${conditionId}`).then((r) => r.data)
}
