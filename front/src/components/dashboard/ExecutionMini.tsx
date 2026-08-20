'use client'

import { useTranslation } from 'react-i18next'
import { useExecutionStore } from '@/lib/store/executionStore'

interface ExecutionMiniProps {
  stockCode?: string
}

const ROW_GRID = '1.2fr 0.9fr 0.9fr 1fr'
const MAX_ROWS = 6
const ROW_HEIGHT = 28
const LIST_HEIGHT = ROW_HEIGHT * MAX_ROWS // 행 개수와 무관하게 박스 높이 고정

/** 우측 주문 패널에 붙는 실시간 체결 스트림 */
export default function ExecutionMini({ stockCode }: ExecutionMiniProps) {
  const { t } = useTranslation()
  const allExecutions = useExecutionStore((s) => s.executions)
  const executions = (stockCode
    ? allExecutions.filter(e => e.stockCode === stockCode)
    : allExecutions
  ).slice(0, MAX_ROWS)

  return (
    <div style={{
      background: '#161b22',
      border: '1px solid #1e2533',
      borderRadius: 14,
      marginTop: 16,
      overflow: 'hidden',
    }}>
      <h3 style={{ fontSize: '0.85rem', fontWeight: 700, color: '#f0f6fc', margin: 0, padding: '14px 16px 8px' }}>
        {t('execution.title')}
      </h3>

      <div style={{
        display: 'grid',
        gridTemplateColumns: ROW_GRID,
        gap: '8px',
        padding: '6px 16px',
        borderTop: '1px solid #1e2533',
        borderBottom: '1px solid #1e2533',
        background: '#0d1117',
      }}>
        <span style={{ fontSize: 11, fontWeight: 600, color: '#8b949e', textAlign: 'right' }}>{t('execution.price')}</span>
        <span style={{ fontSize: 11, fontWeight: 600, color: '#8b949e', textAlign: 'right' }}>{t('execution.volume')}</span>
        <span style={{ fontSize: 11, fontWeight: 600, color: '#8b949e', textAlign: 'right' }}>{t('execution.changeRate')}</span>
        <span style={{ fontSize: 11, fontWeight: 600, color: '#8b949e', textAlign: 'right' }}>{t('execution.time')}</span>
      </div>

      <div style={{ height: LIST_HEIGHT }}>
        {executions.length === 0 ? (
          <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#484f58', fontSize: 12 }}>
            {t('execution.waiting')}
          </div>
        ) : executions.map((item, i) => {
          const rateColor = item.changeRate > 0 ? '#ef5350' : item.changeRate < 0 ? '#1976d2' : '#8b949e'
          const rateSign = item.changeRate > 0 ? '+' : ''
          return (
            <div
              key={`${item.stockCode}-${item.time}-${i}`}
              style={{
                display: 'grid',
                gridTemplateColumns: ROW_GRID,
                gap: '8px',
                height: ROW_HEIGHT,
                alignItems: 'center',
                padding: '0 16px',
                fontSize: 12,
                fontFamily: 'monospace',
                background: i === 0 ? 'rgba(88,166,255,0.05)' : 'transparent',
              }}
            >
              <span style={{ textAlign: 'right', fontWeight: 600, color: item.sign === '1' ? '#ef5350' : item.sign === '5' ? '#1976d2' : '#f0f6fc' }}>
                {item.price.toLocaleString()}
              </span>
              <span style={{ textAlign: 'right', color: '#8b949e' }}>
                {item.volume.toLocaleString()}
              </span>
              <span style={{ textAlign: 'right', color: rateColor }}>
                {rateSign}{item.changeRate.toFixed(2)}%
              </span>
              <span style={{ textAlign: 'right', color: '#6b7280' }}>
                {item.time}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
