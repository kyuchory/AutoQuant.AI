'use client'

import { memo, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useChartStore } from '@/lib/store/chartStore'
import { getStocks } from '@/lib/api/stocks'
import type { StockInfo } from '@/types/stocks'

interface StockSidebarProps {
  selectedStockCode: string
  onSelectStock: (stockCode: string, stockName: string) => void
}

const formatPrice = (price: number) => price.toLocaleString('ko-KR')

/** KIS 전일대비 등락률 기준 색상 */
const getColor = (changeRate: number) => {
  if (changeRate > 0) return { color: '#ef5350', sign: '+' }
  if (changeRate < 0) return { color: '#1976d2', sign: '' }
  return { color: '#d1d4dc', sign: '' }
}

interface StockRowProps {
  stock: StockInfo
  isSelected: boolean
  onSelect: (stockCode: string, stockName: string) => void
}

/**
 * 종목 1개 행. 자기 종목의 시세만 구독해 틱 1건 = 이 행 1개만 리렌더되도록 격리한다
 * (StockSidebar가 prices 맵 전체를 구독하면 다른 종목 틱에도 리스트 전체가 리렌더됨).
 */
const StockRow = memo(function StockRow({ stock, isSelected, onSelect }: StockRowProps) {
  const data = useChartStore((s) => s.prices[stock.stockCode])
  const currentPrice = data?.price && data.price > 0 ? data.price : stock.currentPrice
  const changeRate = data?.changeRate ?? 0
  const { color, sign } = getColor(changeRate)

  return (
    <div
      className={`stock-row ${isSelected ? 'selected' : ''}`}
      onClick={() => onSelect(stock.stockCode, stock.stockName)}
    >
      <span className="stock-name" style={{ color: '#d1d4dc' }}>{stock.stockName}</span>
      <span className="stock-price" style={{ color }}>{formatPrice(currentPrice)}</span>
      <span className="stock-change" style={{ color }}>
        {sign}{changeRate.toFixed(2)}%
      </span>

      <style jsx>{`
        .stock-row {
          display: grid;
          grid-template-columns: 1fr 90px 70px;
          padding: 10px 12px;
          cursor: pointer;
          border-bottom: 1px solid #1e2533;
          transition: background 0.15s;
        }
        .stock-row:hover {
          background: #131920;
        }
        .stock-row.selected {
          background: #1a2332;
        }
        .stock-name {
          font-size: 0.85rem;
          font-weight: 500;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .stock-price {
          font-size: 0.85rem;
          text-align: right;
          font-family: monospace;
        }
        .stock-change {
          font-size: 0.75rem;
          text-align: right;
          font-family: monospace;
        }
      `}</style>
    </div>
  )
})

export default function StockSidebar({ selectedStockCode, onSelectStock }: StockSidebarProps) {
  const { t } = useTranslation()
  const connectionStatus = useChartStore((s) => s.connectionStatus)

  const { data: stocks } = useQuery<StockInfo[]>({
    queryKey: ['stocks'],
    queryFn: getStocks,
    staleTime: 30_000,
  })

  // REST API 초기 데이터를 chartStore에 동기화 (장 마감/서버 재시작 대응)
  useEffect(() => {
    if (!stocks) return
    const store = useChartStore.getState()
    stocks.forEach((s) => {
      if (!store.prices[s.stockCode]) {
        store.updatePrice(s.stockCode, Number(s.currentPrice ?? 0), s.changeRate ?? 0)
      }
    })
  }, [stocks])

  return (
    <div className="stock-sidebar">
      {connectionStatus === 'disconnected' && (
        <div className="connection-badge">⚠ {t('dashboard.connectionLost')}</div>
      )}
      <div className="sidebar-header">
        <span className="text-xs font-medium" style={{ color: '#787b86' }}>{t('sidebar.stockName')}</span>
        <span className="text-xs font-medium text-right" style={{ color: '#787b86' }}>{t('sidebar.currentPrice')}</span>
        <span className="text-xs font-medium text-right" style={{ color: '#787b86' }}>{t('sidebar.prevDayChange')}</span>
      </div>
      <div className="sidebar-list">
        {(stocks ?? []).map((stock) => (
          <StockRow
            key={stock.stockCode}
            stock={stock}
            isSelected={stock.stockCode === selectedStockCode}
            onSelect={onSelectStock}
          />
        ))}
      </div>

      <style jsx>{`
        .stock-sidebar {
          width: 240px;
          min-width: 240px;
          height: 100%;
          background: #0d1117;
          border-right: 1px solid #1e2533;
          display: flex;
          flex-direction: column;
          overflow-y: auto;
        }
        .connection-badge {
          padding: 6px 12px;
          font-size: 0.7rem;
          color: #ef5350;
          background: rgba(239, 83, 80, 0.1);
          border-bottom: 1px solid #1e2533;
        }
        .sidebar-header {
          display: grid;
          grid-template-columns: 1fr 90px 70px;
          padding: 8px 12px;
          border-bottom: 1px solid #1e2533;
        }
        .sidebar-list {
          flex: 1;
          overflow-y: auto;
        }
      `}</style>
    </div>
  )
}
