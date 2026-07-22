'use client'

import { useEffect, useState } from 'react'
import apiClient from '@/lib/api/client'
import { useChartStore } from '@/lib/store/chartStore'
import type { ApiResponse } from '@/types/api'

interface StockInfo {
  stockCode: string
  stockName: string
  currentPrice: number
}

interface StockSidebarProps {
  selectedStockCode: string
  onSelectStock: (stockCode: string, stockName: string) => void
}

export default function StockSidebar({ selectedStockCode, onSelectStock }: StockSidebarProps) {
  const [stocks, setStocks] = useState<StockInfo[]>([])
  const [prevPrices, setPrevPrices] = useState<Record<string, number>>({})
  const prices = useChartStore((s) => s.prices)

  useEffect(() => {
    apiClient.get<ApiResponse<StockInfo[]>>('/stocks')
      .then(r => {
        const list = r.data.data ?? []
        setStocks(list)
        const init: Record<string, number> = {}
        list.forEach(s => { init[s.stockCode] = s.currentPrice })
        setPrevPrices(init)
      })
      .catch(() => {})
  }, [])

  const formatPrice = (price: number) =>
    price.toLocaleString('ko-KR')

  const getChangeRate = (code: string, current: number): { rate: number; prev: number } => {
    const prev = prevPrices[code] ?? current
    if (prev === 0) return { rate: 0, prev: current }
    return { rate: ((current - prev) / prev) * 100, prev }
  }

  const getColor = (current: number, prev: number) => {
    if (current > prev) return { color: '#ef5350', sign: '+' }
    if (current < prev) return { color: '#1976d2', sign: '' }
    return { color: '#d1d4dc', sign: '' }
  }

  return (
    <div className="stock-sidebar">
      <div className="sidebar-header">
        <span className="text-xs font-medium" style={{ color: '#787b86' }}>종목명</span>
        <span className="text-xs font-medium text-right" style={{ color: '#787b86' }}>현재가</span>
        <span className="text-xs font-medium text-right" style={{ color: '#787b86' }}>등락률</span>
      </div>
      <div className="sidebar-list">
        {stocks.map(stock => {
          const currentPrice = prices[stock.stockCode] ?? stock.currentPrice
          const { rate, prev } = getChangeRate(stock.stockCode, currentPrice)
          const { color, sign } = getColor(currentPrice, prev)
          const isSelected = stock.stockCode === selectedStockCode

          return (
            <div
              key={stock.stockCode}
              className={`stock-row ${isSelected ? 'selected' : ''}`}
              onClick={() => onSelectStock(stock.stockCode, stock.stockName)}
            >
              <span className="stock-name" style={{ color: '#d1d4dc' }}>{stock.stockName}</span>
              <span className="stock-price" style={{ color }}>{formatPrice(currentPrice)}</span>
              <span className="stock-change" style={{ color }}>
                {sign}{rate.toFixed(2)}%
              </span>
            </div>
          )
        })}
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
}