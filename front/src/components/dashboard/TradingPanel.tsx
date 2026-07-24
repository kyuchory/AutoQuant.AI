'use client'

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import apiClient from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'

interface OrderResponse {
  historyId: number
  stockCode: string
  orderType: string
  status: string
  executionPrice: number
  executionQuantity: number
  totalAmount: number
  failureReason: string | null
  requestedAt: string
  executedAt: string | null
}

interface TradingPanelProps {
  stockCode: string
  stockName: string
  currentPrice: number
}

const RATIO_OPTIONS = [10, 25, 50, 100] as const

export default function TradingPanel({ stockCode, stockName, currentPrice }: TradingPanelProps) {
  const { t } = useTranslation()
  const [orderType, setOrderType] = useState<'BUY' | 'SELL'>('BUY')
  const [ordDvsn, setOrdDvsn] = useState<'01' | '00'>('01')
  const [price, setPrice] = useState<string>('')
  const [quantity, setQuantity] = useState<number>(1)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<OrderResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleRatio = (ratio: number) => {
    if (ordDvsn === '01' || !price) {
      // 시장가: 수량만 비율 적용
      setQuantity(Math.max(1, Math.floor(ratio)))
    } else {
      // 지정가: 가격 기반 단순 수량 계산
      const estimated = Math.max(1, Math.floor(ratio / 10))
      setQuantity(estimated)
    }
  }

  const handleSubmit = async () => {
    if (loading) return
    setLoading(true)
    setResult(null)
    setError(null)

    try {
      const body: Record<string, string | number> = {
        stockCode,
        orderType,
        quantity,
        ordDvsn,
      }
      // 시장가일 때는 price를 null로, 지정가일 때는 price 포함
      if (ordDvsn === '00' && price) {
        body.price = Number(price)
      }

      const response = await apiClient.post<ApiResponse<OrderResponse>>('/assets/orders', body)
      const data = response.data.data
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

  const estimateTotal = ordDvsn === '00' && price
    ? (Number(price) * quantity).toLocaleString()
    : (currentPrice * quantity).toLocaleString()

  return (
    <div style={{
      background: '#161b22',
      border: '1px solid #1e2533',
      borderRadius: 8,
      padding: '20px',
      color: '#c9d1d9',
      fontFamily: 'system-ui, -apple-system, sans-serif'
    }}>
      <h3 style={{
        fontSize: '0.95rem',
        fontWeight: 600,
        color: '#f0f6fc',
        margin: '0 0 16px 0'
      }}>
        {stockName} ({stockCode})
      </h3>

      {/* 현재가 */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 16,
        padding: '8px 12px',
        background: 'rgba(88,166,255,0.05)',
        borderRadius: 6
      }}>
        <span style={{ fontSize: 12, color: '#8b949e' }}>{t('trading.currentPrice')}</span>
        <span style={{ fontSize: 16, fontWeight: 700, color: '#58a6ff' }}>
          ₩{currentPrice.toLocaleString()}
        </span>
      </div>

      {/* BUY/SELL 탭 */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 16 }}>
        {(['BUY', 'SELL'] as const).map(type => (
          <button
            key={type}
            onClick={() => setOrderType(type)}
            style={{
              flex: 1,
              padding: '8px 0',
              borderRadius: 6,
              border: orderType === type
                ? `1px solid ${type === 'BUY' ? '#ef5350' : '#1976d2'}`
                : '1px solid #30363d',
              background: orderType === type
                ? `${type === 'BUY' ? 'rgba(239,83,80,0.1)' : 'rgba(25,118,210,0.1)'}`
                : 'transparent',
              color: orderType === type
                ? (type === 'BUY' ? '#ef5350' : '#1976d2')
                : '#8b949e',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.15s'
            }}
          >
            {t(`trading.${type.toLowerCase()}`)}
          </button>
        ))}
      </div>

      {/* 시장가/지정가 토글 */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 16 }}>
        <button
          onClick={() => setOrdDvsn('01')}
          style={{
            flex: 1,
            padding: '6px 0',
            borderRadius: 4,
            border: ordDvsn === '01' ? '1px solid #58a6ff' : '1px solid #30363d',
            background: ordDvsn === '01' ? 'rgba(88,166,255,0.1)' : 'transparent',
            color: ordDvsn === '01' ? '#58a6ff' : '#8b949e',
            fontSize: 12,
            fontWeight: 500,
            cursor: 'pointer'
          }}
        >
          {t('trading.marketPrice')}
        </button>
        <button
          onClick={() => setOrdDvsn('00')}
          style={{
            flex: 1,
            padding: '6px 0',
            borderRadius: 4,
            border: ordDvsn === '00' ? '1px solid #58a6ff' : '1px solid #30363d',
            background: ordDvsn === '00' ? 'rgba(88,166,255,0.1)' : 'transparent',
            color: ordDvsn === '00' ? '#58a6ff' : '#8b949e',
            fontSize: 12,
            fontWeight: 500,
            cursor: 'pointer'
          }}
        >
          {t('trading.limitPrice')}
        </button>
      </div>

      {/* 지정가 입력 */}
      {ordDvsn === '00' && (
        <div style={{ marginBottom: 12 }}>
          <label style={{ fontSize: 11, color: '#8b949e', display: 'block', marginBottom: 4 }}>
            {t('trading.orderPrice')}
          </label>
          <input
            type="number"
            value={price}
            onChange={e => setPrice(e.target.value)}
            placeholder={currentPrice.toString()}
            style={{
              width: '100%',
              padding: '8px 12px',
              borderRadius: 6,
              border: '1px solid #30363d',
              background: '#0d1117',
              color: '#c9d1d9',
              fontSize: 13,
              outline: 'none',
              boxSizing: 'border-box'
            }}
            onFocus={e => e.currentTarget.style.borderColor = '#58a6ff'}
            onBlur={e => e.currentTarget.style.borderColor = '#30363d'}
          />
        </div>
      )}

      {/* 수량 + 비율 버튼 */}
      <div style={{ marginBottom: 12 }}>
        <label style={{ fontSize: 11, color: '#8b949e', display: 'block', marginBottom: 4 }}>
          {t('trading.quantity')}
        </label>
        <input
          type="number"
          min={1}
          value={quantity}
          onChange={e => setQuantity(Math.max(1, Number(e.target.value)))}
          style={{
            width: '100%',
            padding: '8px 12px',
            borderRadius: 6,
            border: '1px solid #30363d',
            background: '#0d1117',
            color: '#c9d1d9',
            fontSize: 13,
            outline: 'none',
            boxSizing: 'border-box',
            marginBottom: 8
          }}
          onFocus={e => e.currentTarget.style.borderColor = '#58a6ff'}
          onBlur={e => e.currentTarget.style.borderColor = '#30363d'}
        />
        <div style={{ display: 'flex', gap: 4 }}>
          {RATIO_OPTIONS.map(ratio => (
            <button
              key={ratio}
              onClick={() => handleRatio(ratio)}
              style={{
                flex: 1,
                padding: '4px 0',
                borderRadius: 4,
                border: '1px solid #30363d',
                background: 'transparent',
                color: '#8b949e',
                fontSize: 11,
                fontWeight: 500,
                cursor: 'pointer'
              }}
            >
              {ratio === 100 ? t('trading.max') : `${ratio}%`}
            </button>
          ))}
        </div>
      </div>

      {/* 예상 총액 */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        marginBottom: 16,
        padding: '8px 12px',
        background: 'rgba(210,153,34,0.05)',
        borderRadius: 6
      }}>
        <span style={{ fontSize: 12, color: '#8b949e' }}>{t('trading.estimatedTotal')}</span>
        <span style={{ fontSize: 14, fontWeight: 600, color: '#d29922' }}>
          ₩{estimateTotal}
        </span>
      </div>

      {/* 주문 버튼 */}
      <button
        onClick={handleSubmit}
        disabled={loading}
        style={{
          width: '100%',
          padding: '12px 0',
          borderRadius: 8,
          border: 'none',
          background: loading
            ? '#30363d'
            : orderType === 'BUY' ? '#ef5350' : '#1976d2',
          color: '#fff',
          fontSize: 14,
          fontWeight: 700,
          cursor: loading ? 'not-allowed' : 'pointer',
          transition: 'opacity 0.15s'
        }}
        onMouseEnter={e => { if (!loading) e.currentTarget.style.opacity = '0.9' }}
        onMouseLeave={e => { if (!loading) e.currentTarget.style.opacity = '1' }}
      >
        {loading
          ? t('trading.processing')
          : `${t(`trading.${orderType.toLowerCase()}`)} ${quantity}${t('trading.shares')}`}
      </button>

      {/* 결과 */}
      {result && result.status === 'FILLED' && (
        <div style={{
          marginTop: 12,
          padding: '10px 14px',
          borderRadius: 6,
          background: 'rgba(35,134,54,0.1)',
          border: '1px solid rgba(35,134,54,0.2)',
          fontSize: 12,
          color: '#238636'
        }}>
          ✅ {t('trading.orderFilled')}
          <br />
          {t('trading.executionPrice')}: ₩{result.executionPrice?.toLocaleString()} × {result.executionQuantity}
          ({t('trading.total')}: ₩{result.totalAmount?.toLocaleString()})
        </div>
      )}
      {error && (
        <div style={{
          marginTop: 12,
          padding: '10px 14px',
          borderRadius: 6,
          background: 'rgba(218,54,51,0.1)',
          border: '1px solid rgba(218,54,51,0.2)',
          fontSize: 12,
          color: '#da3633'
        }}>
          ❌ {error}
        </div>
      )}
    </div>
  )
}