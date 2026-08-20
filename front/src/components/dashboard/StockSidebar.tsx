'use client'

import { memo, useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useChartStore } from '@/lib/store/chartStore'
import { getStocks } from '@/lib/api/stocks'
import type { StockInfo } from '@/types/stocks'

interface StockSidebarProps {
  selectedStockCode: string
  onSelectStock: (stockCode: string, stockName: string) => void
}

const WIDTH_KEY = 'dashboard.sidebarWidth'
const MIN_WIDTH = 200
const MAX_WIDTH = 420
const DEFAULT_WIDTH = 264

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
      {isSelected && <span className="selected-bar" />}
      <span className="stock-avatar" style={{ color }}>{stock.stockName.slice(0, 1)}</span>
      <span className="stock-name" style={{ color: isSelected ? '#f0f6fc' : '#c9d1d9' }}>{stock.stockName}</span>
      <span className="stock-price" style={{ color }}>{formatPrice(currentPrice)}</span>
      <span className="stock-change" style={{ color }}>
        {sign}{changeRate.toFixed(2)}%
      </span>

      <style jsx>{`
        .stock-row {
          position: relative;
          display: grid;
          grid-template-columns: 24px minmax(0, 1fr) auto auto;
          align-items: center;
          gap: 8px;
          padding: 11px 14px;
          cursor: pointer;
          transition: background 0.15s;
        }
        .stock-row:hover {
          background: #131920;
        }
        .stock-row.selected {
          background: #16202f;
        }
        .selected-bar {
          position: absolute;
          left: 0;
          top: 6px;
          bottom: 6px;
          width: 3px;
          border-radius: 0 3px 3px 0;
          background: #1f6feb;
        }
        .stock-avatar {
          width: 24px;
          height: 24px;
          border-radius: 7px;
          background: #1a2233;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 0.68rem;
          font-weight: 700;
          flex-shrink: 0;
        }
        .stock-name {
          min-width: 0;
          font-size: 0.85rem;
          font-weight: 500;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .stock-price {
          font-size: 0.85rem;
          font-weight: 600;
          text-align: right;
          font-family: monospace;
          white-space: nowrap;
        }
        .stock-change {
          font-size: 0.75rem;
          text-align: right;
          font-family: monospace;
          white-space: nowrap;
        }
      `}</style>
    </div>
  )
})

export default function StockSidebar({ selectedStockCode, onSelectStock }: StockSidebarProps) {
  const { t } = useTranslation()
  const connectionStatus = useChartStore((s) => s.connectionStatus)
  const [width, setWidth] = useState(() => {
    if (typeof window === 'undefined') return DEFAULT_WIDTH
    const saved = Number(localStorage.getItem(WIDTH_KEY))
    return saved >= MIN_WIDTH && saved <= MAX_WIDTH ? saved : DEFAULT_WIDTH
  })
  const draggingRef = useRef(false)
  const dragStartRef = useRef({ x: 0, width: DEFAULT_WIDTH })

  const handleDragStart = useCallback((e: React.MouseEvent) => {
    draggingRef.current = true
    dragStartRef.current = { x: e.clientX, width }
    e.preventDefault()
  }, [width])

  useEffect(() => {
    const handleMove = (e: MouseEvent) => {
      if (!draggingRef.current) return
      const next = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, dragStartRef.current.width + (e.clientX - dragStartRef.current.x)))
      setWidth(next)
    }
    const handleUp = () => {
      if (!draggingRef.current) return
      draggingRef.current = false
      localStorage.setItem(WIDTH_KEY, String(width))
    }
    window.addEventListener('mousemove', handleMove)
    window.addEventListener('mouseup', handleUp)
    return () => {
      window.removeEventListener('mousemove', handleMove)
      window.removeEventListener('mouseup', handleUp)
    }
  }, [width])

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
    <div className="stock-sidebar" style={{ width, minWidth: MIN_WIDTH, maxWidth: MAX_WIDTH }}>
      {connectionStatus === 'disconnected' && (
        <div className="connection-badge">⚠ {t('dashboard.connectionLost')}</div>
      )}
      <div className="sidebar-title">{t('sidebar.watchlist')}</div>
      <div className="sidebar-header">
        <span />
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

      {/* 드래그로 너비 조절 */}
      <div className="resize-handle" onMouseDown={handleDragStart}>
        <div className="resize-grip">
          <span />
          <span />
          <span />
        </div>
      </div>

      <style jsx>{`
        .stock-sidebar {
          position: relative;
          height: 100%;
          background: #0d1117;
          border-right: 1px solid #1e2533;
          display: flex;
          flex-direction: column;
          overflow-y: auto;
          overflow-x: hidden;
          flex-shrink: 0;
        }
        .connection-badge {
          padding: 6px 12px;
          font-size: 0.7rem;
          color: #ef5350;
          background: rgba(239, 83, 80, 0.1);
          border-bottom: 1px solid #1e2533;
        }
        .sidebar-title {
          padding: 16px 14px 4px;
          font-size: 0.9rem;
          font-weight: 700;
          color: #f0f6fc;
        }
        .sidebar-header {
          display: grid;
          grid-template-columns: 24px minmax(0, 1fr) auto auto;
          gap: 8px;
          padding: 8px 14px;
          border-bottom: 1px solid #1e2533;
        }
        .sidebar-header span {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .sidebar-list {
          flex: 1;
          overflow-y: auto;
        }
        .resize-handle {
          position: absolute;
          top: 0;
          right: -5px;
          width: 10px;
          height: 100%;
          cursor: col-resize;
          z-index: 5;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .resize-handle:hover,
        .resize-handle:active {
          background: rgba(31, 111, 235, 0.2);
        }
        .resize-grip {
          width: 6px;
          height: 52px;
          border-radius: 4px;
          background: #333b4d;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 5px;
          transition: background 0.15s;
        }
        .resize-handle:hover .resize-grip,
        .resize-handle:active .resize-grip {
          background: #1f6feb;
        }
        .resize-grip span {
          width: 4px;
          height: 4px;
          border-radius: 50%;
          background: #8b95a8;
          flex-shrink: 0;
        }
        .resize-handle:hover .resize-grip span,
        .resize-handle:active .resize-grip span {
          background: #fff;
        }
      `}</style>
    </div>
  )
}
