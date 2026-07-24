'use client'

import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import apiClient from '@/lib/api/client'
import { useChartStore } from '@/lib/store/chartStore'
import type { ApiResponse } from '@/types/api'

interface StockInfo {
  stockCode: string
  stockName: string
  currentPrice: number
  changeRate: number
}

interface StockSidebarProps {
  selectedStockCode: string
  onSelectStock: (stockCode: string, stockName: string) => void
}

export default function StockSidebar({ selectedStockCode, onSelectStock }: StockSidebarProps) {
  const { t } = useTranslation()
  const [stocks, setStocks] = useState<StockInfo[]>([])
  const prices = useChartStore((s) => s.prices)

  useEffect(() => {
    apiClient.get<ApiResponse<StockInfo[]>>('/stocks')
      .then(r => {
        const list = r.data.data ?? []
        setStocks(list)
        // REST API 초기 데이터를 chartStore에 반영 (장 마감/서버 재시작 대응)
        const store = useChartStore.getState()
        list.forEach(s => {
          if (!store.prices[s.stockCode]) {
            store.updatePrice(s.stockCode, Number(s.currentPrice ?? 0), s.changeRate ?? 0)
          }
        })
      })
      .catch(() => {})
  }, [])

  const formatPrice = (price: number) =>
    price.toLocaleString('ko-KR')

  /** KIS 전일대비 등락률 기준 색상 */
  const getColor = (changeRate: number) => {
    if (changeRate > 0) return { color: '#ef5350', sign: '+' }
    if (changeRate < 0) return { color: '#1976d2', sign: '' }
    return { color: '#d1d4dc', sign: '' }
  }

  return (
    <div className="stock-sidebar">
      <div className="sidebar-header">
        <span className="text-xs font-medium" style={{ color: '#787b86' }}>{t('sidebar.stockName')}</span>
        <span className="text-xs font-medium text-right" style={{ color: '#787b86' }}>{t('sidebar.currentPrice')}</span>
        <span className="text-xs font-medium text-right" style={{ color: '#787b86' }}>{t('sidebar.prevDayChange')}</span>
      </div>
      <div className="sidebar-list">
        {stocks.map(stock => {
          const data = prices[stock.stockCode]
          const currentPrice = data?.price ?? stock.currentPrice
          const changeRate = data?.changeRate ?? 0
          const { color, sign } = getColor(changeRate)
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
                {sign}{changeRate.toFixed(2)}%
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