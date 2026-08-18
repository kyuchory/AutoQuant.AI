export type TriggerType =
  | 'PRICE'
  | 'PROFIT_TARGET'
  | 'STOP_LOSS'
  | 'TRAILING_STOP'
  | 'AI_SCORE'

export type BaseType = 'CURRENT_PRICE' | 'AVG_PRICE' | 'HIGHEST_PRICE' | 'AI_SCORE'
export type CompareType = 'ABOVE' | 'BELOW'
export type OrderPriceType = 'MARKET' | 'LIMIT'
export type ExecutionMode = 'AUTO' | 'MANUAL'

export interface TriggerRequest {
  triggerType: TriggerType
  baseType: BaseType
  compareType: CompareType
  targetValue: number
  isRate?: boolean
}

export interface TradingConditionRequest {
  stockCode: string
  orderType: 'BUY' | 'SELL'
  orderQuantity: number
  orderPriceType?: OrderPriceType
  limitPrice?: number
  conditionLogic?: 'AND' | 'OR'
  executionMode?: ExecutionMode
  isPersistent?: boolean
  triggers: TriggerRequest[]
}

export interface TriggerResponse {
  triggerId: number
  triggerType: TriggerType
  baseType: BaseType
  compareType: CompareType
  targetValue: number
  isRate: boolean
  trailingHighest: number | null
}

export interface TradingConditionResponse {
  conditionId: number
  stockCode: string
  orderType: 'BUY' | 'SELL'
  orderQuantity: number
  orderPriceType: OrderPriceType
  limitPrice: number | null
  conditionLogic: 'AND' | 'OR'
  executionMode: ExecutionMode
  isActive: boolean
  isPersistent: boolean
  triggers: TriggerResponse[]
  createdAt: string
}
