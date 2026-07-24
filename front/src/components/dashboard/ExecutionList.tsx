'use client'

import { useTranslation } from 'react-i18next'
import { useExecutionStore } from '@/lib/store/executionStore'

interface ExecutionListProps {
  stockCode?: string
}

export default function ExecutionList({ stockCode }: ExecutionListProps) {
  const { t } = useTranslation()
  const allExecutions = useExecutionStore((s) => s.executions)
  const executions = stockCode
    ? allExecutions.filter(e => e.stockCode === stockCode)
    : allExecutions

  const HEADERS = [
    { key: 'price', align: 'right' as const },
    { key: 'volume', align: 'right' as const },
    { key: 'changeRate', align: 'left' as const },
    { key: 'accVolume', align: 'left' as const },
    { key: 'time', align: 'left' as const },
  ]

  if (executions.length === 0) {
    return (
      <div style={{
        marginTop: 16,
        padding: '28px 16px',
        textAlign: 'center',
        color: '#484f58',
        fontSize: 13,
        background: '#161b22',
        border: '1px solid #1e2533',
        borderRadius: 8
      }}>
        {t('execution.waiting')}
      </div>
    )
  }

  return (
    <div style={{
      background: '#161b22',
      border: '1px solid #1e2533',
      borderRadius: 8,
      display: 'flex',
      flexDirection: 'column',
      flex: 1,
      minHeight: 0
    }}>
      {/* 헤더 */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1.2fr 1fr 1fr 1.3fr 1.1fr',
        gap: '12px',
        padding: '10px 16px',
        borderBottom: '1px solid #1e2533',
        background: '#0d1117',
        flexShrink: 0
      }}>
        {HEADERS.map(h => (
          <span key={h.key} style={{
            fontSize: 12,
            fontWeight: 600,
            color: '#8b949e',
            textAlign: h.align,
            paddingRight: h.align === 'right' ? 8 : 0
          }}>
            {t(`execution.${h.key}`)}
          </span>
        ))}
      </div>

      {/* 리스트 */}
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
        {executions.map((item, i) => {
          const rateColor = item.changeRate > 0 ? '#ef5350' : item.changeRate < 0 ? '#1976d2' : '#c9d1d9'
          const rateSign = item.changeRate > 0 ? '+' : ''
          return (
            <div
              key={`${item.stockCode}-${item.time}-${i}`}
              style={{
                display: 'grid',
                gridTemplateColumns: '1.2fr 1fr 1fr 1.3fr 1.1fr',
                gap: '12px',
                padding: '8px 16px',
                whiteSpace: 'nowrap',
                borderBottom: '1px solid #1e2533',
                fontSize: 13,
                fontFamily: 'monospace',

                color: '#c9d1d9',
                background: i === 0 ? 'rgba(88,166,255,0.04)' : 'transparent'
              }}
            >
              <span style={{ textAlign: 'right', fontWeight: 600, color: '#f0f6fc' }}>
                {item.price.toLocaleString()}
              </span>
              <span style={{ textAlign: 'right', color: item.sign === '1' ? '#ef5350' : item.sign === '5' ? '#1976d2' : '#8b949e' }}>
                {item.volume.toLocaleString()}
              </span>
              <span style={{ color: rateColor }}>
                {rateSign}{item.changeRate.toFixed(2)}%
              </span>
              <span style={{ color: '#8b949e' }}>
                {item.accumulatedVolume.toLocaleString()}
              </span>
              <span style={{ color: '#6b7280' }}>
                {item.time}
              </span>
            </div>
          )
        })}
      </div>

      <style jsx>{`
        /* 숨겨진 스크롤바 */
        div::-webkit-scrollbar { display: none; }
      `}</style>
    </div>
  )
}