'use client'

import { useCallback, useState } from 'react'
import StockSidebar from '@/components/dashboard/StockSidebar'
import CandleChart, { type ChartHeaderData } from '@/components/dashboard/CandleChart'
import NewsTicker from '@/components/dashboard/NewsTicker'
import TradingPanel from '@/components/dashboard/TradingPanel'
import ExecutionMini from '@/components/dashboard/ExecutionMini'
import { useChartStore } from '@/lib/store/chartStore'

export default function DashboardPage() {
  const [selectedStockCode, setSelectedStockCode] = useState('005930')
  const [selectedStockName, setSelectedStockName] = useState('삼성전자')
  const [dayRange, setDayRange] = useState<ChartHeaderData | null>(null)
  const selectedPrice = useChartStore((s) => s.prices[selectedStockCode])

  const handleSelectStock = (stockCode: string, stockName: string) => {
    setDayRange(null)
    setSelectedStockCode(stockCode)
    setSelectedStockName(stockName)
  }

  const handleHeaderUpdate = useCallback((data: ChartHeaderData) => {
    setDayRange(data)
  }, [])

  const currentPrice = selectedPrice?.price ?? dayRange?.currentPrice ?? 0
  const changeRate = selectedPrice?.changeRate ?? dayRange?.changeRate

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: '#0a0e17', overflow: 'hidden' }}>
      {/* 뉴스 티커가 사이드바까지 뚫고 전체 폭을 차지 */}
      <NewsTicker />
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minHeight: 0 }}>
        <StockSidebar
          selectedStockCode={selectedStockCode}
          onSelectStock={handleSelectStock}
        />
        {/* 페이지 전체에 스크롤은 이 영역 하나만 — 중앙/우측이 따로 스크롤되지 않는다 */}
        <div style={{ flex: 1, minWidth: 0, overflowY: 'auto' }}>
          <div style={{ display: 'flex', gap: 20, padding: 20, alignItems: 'flex-start' }}>
            {/* 중앙: 차트 (실시간 체결은 우측 패널로 통합) */}
            <div style={{ flex: 1, minWidth: 0 }}>
              <CandleChart
                stockCode={selectedStockCode}
                stockName={selectedStockName}
                onHeaderUpdate={handleHeaderUpdate}
              />
            </div>

            {/* 우측: 주문 패널 — 스크롤해도 화면에 계속 붙어있음 */}
            <div style={{ width: 360, minWidth: 360, position: 'sticky', top: 0 }}>
              <TradingPanel
                stockCode={selectedStockCode}
                stockName={selectedStockName}
                currentPrice={currentPrice}
                changeRate={changeRate}
                dayHigh={dayRange?.highPrice}
                dayLow={dayRange?.lowPrice}
              />
              <ExecutionMini stockCode={selectedStockCode} />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}