'use client'

import { useState } from 'react'
import StockSidebar from '@/components/dashboard/StockSidebar'
import CandleChart from '@/components/dashboard/CandleChart'
import NewsTicker from '@/components/dashboard/NewsTicker'
import TradingPanel from '@/components/dashboard/TradingPanel'
import ExecutionList from '@/components/dashboard/ExecutionList'
import { useChartStore } from '@/lib/store/chartStore'

export default function DashboardPage() {
  const [selectedStockCode, setSelectedStockCode] = useState('005930')
  const [selectedStockName, setSelectedStockName] = useState('삼성전자')
  const prices = useChartStore((s) => s.prices)

  const handleSelectStock = (stockCode: string, stockName: string) => {
    setSelectedStockCode(stockCode)
    setSelectedStockName(stockName)
  }

  const currentPrice = prices[selectedStockCode]?.price ?? 0

  return (
    <div style={{ display: 'flex', height: '100vh', background: '#0d1117', overflow: 'hidden' }}>
      <StockSidebar
        selectedStockCode={selectedStockCode}
        onSelectStock={handleSelectStock}
      />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <NewsTicker />
        <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <CandleChart
              stockCode={selectedStockCode}
              stockName={selectedStockName}
            />
          </div>
          <div style={{
            width: 410,
            minWidth: 410,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            padding: '16px'
          }}>
            <TradingPanel
              stockCode={selectedStockCode}
              stockName={selectedStockName}
              currentPrice={currentPrice}
            />
            <div style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
              marginTop: 16,
            }}>
              <ExecutionList stockCode={selectedStockCode} />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}