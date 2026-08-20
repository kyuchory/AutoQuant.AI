'use client'

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createOrder } from '@/lib/api/assets'
import { createCondition } from '@/lib/api/conditions'
import type { OrderRequest, OrderResponse } from '@/types/assets'

interface TradingPanelProps {
  stockCode: string
  stockName: string
  currentPrice: number
  dayHigh?: number
  dayLow?: number
  changeRate?: number
}

const RATIO_OPTIONS = [10, 25, 50, 100] as const

type Mode = 'MARKET' | 'LIMIT' | 'STOP'

export default function TradingPanel({ stockCode, stockName, currentPrice, dayHigh, dayLow, changeRate }: TradingPanelProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [orderType, setOrderType] = useState<'BUY' | 'SELL'>('BUY')
  const [mode, setMode] = useState<Mode>('MARKET')
  const [orderPrice, setOrderPrice] = useState<string>('')      // LIMIT: 주문가 / STOP: 감시가
  const [stopLimitPrice, setStopLimitPrice] = useState<string>('') // STOP의 지정가(선택)
  const [quantity, setQuantity] = useState<number>(1)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<OrderResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const stopMutation = useMutation({
    mutationFn: createCondition,
    onSuccess: () => {
      setResult(null)
      setError(t('trading.conditionRegistered'))
      setOrderPrice('')
      setStopLimitPrice('')
      // 대시보드에서 등록해도 이미 열려있는 /conditions 페이지가 즉시 반영되도록 캐시 무효화
      // (conditions/page.tsx의 등록 뮤테이션과 동일한 queryKey를 맞춘다)
      queryClient.invalidateQueries({ queryKey: ['conditions'] })
    },
    onError: (e: Error) => setError(e.message),
  })

  const handleRatio = (ratio: number) => {
    if (mode === 'MARKET' || !orderPrice) {
      setQuantity(Math.max(1, Math.floor(ratio)))
    } else {
      const estimated = Math.max(1, Math.floor(ratio / 10))
      setQuantity(estimated)
    }
  }

  /** 일반 주문 (시장/지정) */
  const handleSubmit = async () => {
    if (loading) return
    setLoading(true)
    setResult(null)
    setError(null)

    try {
      const body: OrderRequest = {
        stockCode,
        orderType,
        quantity,
        ordDvsn: mode === 'LIMIT' ? '00' : '01',
      }
      if (mode === 'LIMIT' && orderPrice) {
        body.price = Number(orderPrice)
      }

      const response = await createOrder(body)
      const data = response.data
      if (data) {
        setResult(data)
        if (data.status === 'FAILED') {
          setError(data.failureReason ?? t('trading.orderFailed'))
        }
      }
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      setError(err?.response?.data?.message ?? t('trading.orderFailed'))
    } finally {
      setLoading(false)
    }
  }

  /** 스탑/자동 조건 등록 */
  const handleStopSubmit = () => {
    const triggerPrice = parseFloat(orderPrice)
    if (!triggerPrice || triggerPrice <= 0) {
      setError(t('trading.triggerPricePlaceholder'))
      return
    }
    const stopType = orderType === 'BUY' ? 'ABOVE' : 'BELOW'

    stopMutation.mutate({
      stockCode,
      orderType,
      orderQuantity: quantity,
      orderPriceType: 'MARKET',
      conditionLogic: 'AND',
      triggers: [{
        triggerType: 'PRICE',
        baseType: 'CURRENT_PRICE',
        compareType: stopType,
        targetValue: triggerPrice,
        isRate: false,
      }],
    })
  }

  const estimateTotal = mode === 'LIMIT' && orderPrice
    ? (Number(orderPrice) * quantity).toLocaleString()
    : (currentPrice * quantity).toLocaleString()

  const submitLabel = mode === 'STOP'
    ? t('conditions.startWatch')
    : `${t(`trading.${orderType.toLowerCase()}`)} ${quantity}${t('trading.shares')}`

  const trendColor = changeRate === undefined ? '#58a6ff' : changeRate > 0 ? '#ef5350' : changeRate < 0 ? '#1976d2' : '#8b949e'
  const rangePct = dayHigh && dayLow && dayHigh > dayLow
    ? Math.min(100, Math.max(0, ((currentPrice - dayLow) / (dayHigh - dayLow)) * 100))
    : null

  return (
    <div style={{
      background: '#161b22',
      border: '1px solid #1e2533',
      borderRadius: 14,
      padding: '20px',
      color: '#c9d1d9',
      fontFamily: 'system-ui, -apple-system, sans-serif'
    }}>
      <h3 style={{ fontSize: '0.95rem', fontWeight: 600, color: '#f0f6fc', margin: '0 0 12px 0' }}>
        {stockName} <span style={{ color: '#6b7280', fontWeight: 400 }}>{stockCode}</span>
      </h3>

      {/* 현재가 */}
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 14 }}>
        <span style={{ fontSize: 22, fontWeight: 800, color: trendColor, fontFamily: 'monospace', letterSpacing: '-0.02em' }}>
          ₩{currentPrice.toLocaleString()}
        </span>
        {changeRate !== undefined && (
          <span style={{ fontSize: 13, fontWeight: 700, color: trendColor, fontFamily: 'monospace' }}>
            {changeRate > 0 ? '+' : ''}{changeRate.toFixed(2)}%
          </span>
        )}
      </div>

      {/* 당일 등락 범위 */}
      {rangePct !== null && (
        <div style={{ marginBottom: 18 }}>
          <div style={{ position: 'relative', height: 4, borderRadius: 2, background: 'linear-gradient(90deg, #1976d2, #30363d, #ef5350)' }}>
            <div style={{
              position: 'absolute', top: -3, left: `${rangePct}%`, transform: 'translateX(-50%)',
              width: 10, height: 10, borderRadius: '50%', background: '#f0f6fc', border: '2px solid #161b22'
            }} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6 }}>
            <span style={{ fontSize: 10, color: '#1976d2', fontFamily: 'monospace' }}>{t('chart.low')} {dayLow?.toLocaleString()}</span>
            <span style={{ fontSize: 10, color: '#ef5350', fontFamily: 'monospace' }}>{t('chart.high')} {dayHigh?.toLocaleString()}</span>
          </div>
        </div>
      )}

      {/* BUY/SELL 탭 */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 16 }}>
        {(['BUY', 'SELL'] as const).map(type => (
          <button
            key={type}
            onClick={() => setOrderType(type)}
            style={{
              flex: 1, padding: '8px 0', borderRadius: 6,
              border: orderType === type ? `1px solid ${type === 'BUY' ? '#ef5350' : '#1976d2'}` : '1px solid #30363d',
              background: orderType === type ? `${type === 'BUY' ? 'rgba(239,83,80,0.1)' : 'rgba(25,118,210,0.1)'}` : 'transparent',
              color: orderType === type ? (type === 'BUY' ? '#ef5350' : '#1976d2') : '#8b949e',
              fontSize: 13, fontWeight: 600, cursor: 'pointer', transition: 'all 0.15s'
            }}
          >
            {t(`trading.${type.toLowerCase()}`)}
          </button>
        ))}
      </div>

      {/* 시장가/지정가/스탑 탭 */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 16 }}>
        {([['MARKET', 'marketPrice'], ['LIMIT', 'limitPrice'], ['STOP', 'stopAuto']] as [Mode, string][]).map(([m, key]) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            style={{
              flex: 1, padding: '6px 0', borderRadius: 4,
              border: mode === m ? '1px solid #58a6ff' : '1px solid #30363d',
              background: mode === m ? 'rgba(88,166,255,0.1)' : 'transparent',
              color: mode === m ? '#58a6ff' : '#8b949e',
              fontSize: 12, fontWeight: 500, cursor: 'pointer'
            }}
          >
            {t(`trading.${key}`)}
          </button>
        ))}
      </div>

      {/* LIMIT: 주문가 입력 */}
      {mode === 'LIMIT' && (
        <div style={{ marginBottom: 12 }}>
          <label style={{ fontSize: 11, color: '#8b949e', display: 'block', marginBottom: 4 }}>
            {t('trading.orderPrice')}
          </label>
          <input type="number" value={orderPrice} onChange={e => setOrderPrice(e.target.value)}
            placeholder={currentPrice.toString()} style={inputStyle}
            onFocus={e => e.currentTarget.style.borderColor = '#58a6ff'}
            onBlur={e => e.currentTarget.style.borderColor = '#30363d'} />
        </div>
      )}

      {/* STOP: 감시가 입력 */}
      {mode === 'STOP' && (
        <div style={{ marginBottom: 12 }}>
          <label style={{ fontSize: 11, color: '#8b949e', display: 'block', marginBottom: 4 }}>
            {t('trading.triggerPrice')}
          </label>
          <input type="number" value={orderPrice} onChange={e => setOrderPrice(e.target.value)}
            placeholder={currentPrice.toString()} style={inputStyle}
            onFocus={e => e.currentTarget.style.borderColor = '#58a6ff'}
            onBlur={e => e.currentTarget.style.borderColor = '#30363d'} />
        </div>
      )}

      {/* 수량 + 비율 버튼 */}
      <div style={{ marginBottom: 12 }}>
        <label style={{ fontSize: 11, color: '#8b949e', display: 'block', marginBottom: 4 }}>
          {t('trading.quantity')}
        </label>
        <input type="number" min={1} value={quantity} onChange={e => setQuantity(Math.max(1, Number(e.target.value)))}
          style={{ ...inputStyle, marginBottom: 8 }}
          onFocus={e => e.currentTarget.style.borderColor = '#58a6ff'}
          onBlur={e => e.currentTarget.style.borderColor = '#30363d'} />
        <div style={{ display: 'flex', gap: 4 }}>
          {RATIO_OPTIONS.map(ratio => (
            <button key={ratio} onClick={() => handleRatio(ratio)}
              style={{ flex: 1, padding: '4px 0', borderRadius: 4, border: '1px solid #30363d', background: 'transparent', color: '#8b949e', fontSize: 11, fontWeight: 500, cursor: 'pointer' }}>
              {ratio === 100 ? t('trading.max') : `${ratio}%`}
            </button>
          ))}
        </div>
      </div>

      {/* 예상 총액 (스탑 제외) */}
      {mode !== 'STOP' && (
        <div style={{
          display: 'flex', justifyContent: 'space-between', marginBottom: 16,
          padding: '8px 12px', background: 'rgba(210,153,34,0.05)', borderRadius: 6
        }}>
          <span style={{ fontSize: 12, color: '#8b949e' }}>{t('trading.estimatedTotal')}</span>
          <span style={{ fontSize: 14, fontWeight: 600, color: '#d29922' }}>₩{estimateTotal}</span>
        </div>
      )}

      {/* 주문/등록 버튼 */}
      <button
        onClick={mode === 'STOP' ? handleStopSubmit : handleSubmit}
        disabled={loading || stopMutation.isPending}
        style={{
          width: '100%', padding: '12px 0', borderRadius: 8, border: 'none',
          background: loading || stopMutation.isPending ? '#30363d'
            : mode === 'STOP' ? '#6f42c1' : orderType === 'BUY' ? '#ef5350' : '#1976d2',
          color: '#fff', fontSize: 14, fontWeight: 700,
          cursor: loading || stopMutation.isPending ? 'not-allowed' : 'pointer',
          transition: 'opacity 0.15s'
        }}
        onMouseEnter={e => { if (!loading && !stopMutation.isPending) e.currentTarget.style.opacity = '0.9' }}
        onMouseLeave={e => { if (!loading && !stopMutation.isPending) e.currentTarget.style.opacity = '1' }}
      >
        {loading ? t('trading.processing') : stopMutation.isPending ? t('common.processing') : submitLabel}
      </button>

      {/* 일반 주문 결과 */}
      {result && result.status === 'FILLED' && (
        <div style={{
          marginTop: 12, padding: '10px 14px', borderRadius: 6,
          background: 'rgba(35,134,54,0.1)', border: '1px solid rgba(35,134,54,0.2)', fontSize: 12, color: '#238636'
        }}>
          ✅ {t('trading.orderFilled')}
          <br />
          {t('trading.executionPrice')}: ₩{result.executionPrice?.toLocaleString()} × {result.executionQuantity}
          ({t('trading.total')}: ₩{result.totalAmount?.toLocaleString()})
        </div>
      )}

      {/* 에러/성공 메시지 */}
      {error && (
        <div style={{
          marginTop: 12, padding: '10px 14px', borderRadius: 6,
          background: 'rgba(88,166,255,0.08)', border: '1px solid rgba(88,166,255,0.2)', fontSize: 12, color: '#58a6ff'
        }}>
          {error}
        </div>
      )}
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '8px 12px', borderRadius: 6, border: '1px solid #30363d',
  background: '#0d1117', color: '#c9d1d9', fontSize: 13, outline: 'none', boxSizing: 'border-box',
}