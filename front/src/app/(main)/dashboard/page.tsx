'use client'

import { useState } from 'react'
import StockSidebar from '@/components/dashboard/StockSidebar'
import CandleChart from '@/components/dashboard/CandleChart'
import NewsTicker from '@/components/dashboard/NewsTicker'

export default function DashboardPage() {
  const [selectedStockCode, setSelectedStockCode] = useState('005930')
  const [selectedStockName, setSelectedStockName] = useState('삼성전자')

  const handleSelectStock = (stockCode: string, stockName: string) => {
    setSelectedStockCode(stockCode)
    setSelectedStockName(stockName)
  }

  return (
    <div style={{ display: 'flex', height: '100vh', background: '#0d1117', overflow: 'hidden' }}>
      <StockSidebar
        selectedStockCode={selectedStockCode}
        onSelectStock={handleSelectStock}
      />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <NewsTicker />
        <CandleChart
          stockCode={selectedStockCode}
          stockName={selectedStockName}
        />
      </div>
    </div>
  )
}