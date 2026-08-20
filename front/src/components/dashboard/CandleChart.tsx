'use client'

import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createChart, CandlestickSeries, HistogramSeries, ISeriesApi, Time } from 'lightweight-charts'
import { getDailyChart, getMinuteChart } from '@/lib/api/charts'
import { useChartStore } from '@/lib/store/chartStore'
import type { PeriodCode } from '@/types/chart'

interface CandleChartProps {
  stockCode: string
  stockName: string
}

const PERIOD_TABS: { labelKey: string; code: PeriodCode }[] = [
  { labelKey: 'chart.periodMinute', code: 'MINUTE' },
  { labelKey: 'chart.periodDay', code: 'D' },
  { labelKey: 'chart.periodWeek', code: 'W' },
  { labelKey: 'chart.periodMonth', code: 'M' },
  { labelKey: 'chart.periodYear', code: 'Y' },
]

export default function CandleChart({ stockCode, stockName }: CandleChartProps) {
  const { t } = useTranslation()
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<ReturnType<typeof createChart> | null>(null)
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const volumeSeriesRef = useRef<ISeriesApi<'Histogram'> | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const activeRequestRef = useRef<string>('')  // "stockCode:period" for race condition guard
  const prevCloseRef = useRef<number | null>(null)

  const [period, setPeriod] = useState<PeriodCode>('D')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [headerData, setHeaderData] = useState<{
    currentPrice: number; changeAmount: number; changeRate: number
    openPrice: number; highPrice: number; lowPrice: number
  } | null>(null)

  // 이 종목의 시세만 구독 — 다른 종목 틱으로 인한 불필요한 리렌더 방지
  const priceData = useChartStore((s) => s.prices[stockCode])

  // 초기화
  useEffect(() => {
    if (!containerRef.current) return

    const chart = createChart(containerRef.current)
    chart.applyOptions({
      layout: { background: { color: '#0d1117' }, textColor: '#787b86' },
      grid: { vertLines: { color: '#1e2533' }, horzLines: { color: '#1e2533' } },
      crosshair: {
        mode: 0,
        vertLine: { color: '#758696', labelBackgroundColor: '#1e2533' },
        horzLine: { color: '#758696', labelBackgroundColor: '#1e2533' },
      },
      rightPriceScale: { borderColor: '#1e2533', textColor: '#787b86' },
      timeScale: { borderColor: '#1e2533', timeVisible: true, secondsVisible: false },
      width: containerRef.current.clientWidth,
      height: 480,
    })

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#ef5350', downColor: '#1976d2',
      borderUpColor: '#ef5350', borderDownColor: '#1976d2',
      wickUpColor: '#ef5350', wickDownColor: '#1976d2',
    })
    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'volume' }, priceScaleId: 'volume',
    })
    chart.priceScale('volume').applyOptions({ scaleMargins: { top: 0.85, bottom: 0 } })

    chartRef.current = chart
    candleSeriesRef.current = candleSeries
    volumeSeriesRef.current = volumeSeries

    const handleResize = () => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.applyOptions({ width: containerRef.current.clientWidth })
      }
    }
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      if (chartRef.current) {
        chartRef.current.remove()
        chartRef.current = null
      }
    }
  }, [])

  // 데이터 fetch (race condition 방지)
  useEffect(() => {
    if (!candleSeriesRef.current || !volumeSeriesRef.current) return

    // 이전 요청 취소
    if (abortRef.current) abortRef.current.abort()
    const requestId = `${stockCode}:${period}`
    activeRequestRef.current = requestId

    // 로딩 표시 (차트 데이터는 유지 — setData([]) 제거)
    setLoading(true)
    setError(null)

    const p = period === 'MINUTE'
      ? getMinuteChart(stockCode)
      : getDailyChart(stockCode, period)

    p.then(res => {
      // stale check: stockCode나 period가 fetch 중에 바뀌었으면 무시
      if (activeRequestRef.current !== requestId) return

      if (!res.success || !res.data) {
        setError(t('chart.loadError'))
        setLoading(false)
        return
      }

      const { data } = res
      candleSeriesRef.current!.setData(data.candles.map(c => ({
        time: c.time as Time, open: c.open, high: c.high, low: c.low, close: c.close,
      })))
      volumeSeriesRef.current!.setData(data.candles.map(c => ({
        time: c.time as Time, value: c.volume,
        color: c.close >= c.open ? 'rgba(239, 83, 80, 0.3)' : 'rgba(25, 118, 210, 0.3)',
      })))

      setHeaderData({
        currentPrice: data.currentPrice,
        changeAmount: data.changeAmount,
        changeRate: data.changeRate,
        openPrice: data.openPrice,
        highPrice: data.highPrice,
        lowPrice: data.lowPrice,
      })
      prevCloseRef.current = data.currentPrice - data.changeAmount
      setLoading(false)
    }).catch(() => {
      if (activeRequestRef.current !== requestId) return
      setError(t('chart.loadError'))
      setLoading(false)
    })
  }, [stockCode, period, t])

  // 실시간 가격 업데이트 (캔들 + 거래량 + 헤더)
  const realtimePrice = priceData?.price
  const realtimeChangeRate = priceData?.changeRate
  const realtimeVolume = priceData?.volume
  useEffect(() => {
    if (!realtimePrice || realtimePrice <= 0) return
    // 종목/기간 전환 중(REST 응답 대기 중) 도착한 틱이 이전 차트 데이터에 섞이는 것을 방지
    if (activeRequestRef.current !== `${stockCode}:${period}`) return

    if (candleSeriesRef.current && candleSeriesRef.current.data().length) {
      const data = candleSeriesRef.current.data()
      const last = data[data.length - 1]
      if (last && 'open' in last) {  // WhitespaceData 가드
        const lastTime = last.time as number
        // 분봉은 실시간 틱이 분 경계를 넘으면 새 봉을 열어야 함(그 외 기간은 당일 하루짜리 봉 유지)
        const nowBucket = period === 'MINUTE' ? Math.floor(Date.now() / 60000) * 60 : lastTime
        const opensNewBar = period === 'MINUTE' && nowBucket > lastTime

        if (opensNewBar) {
          candleSeriesRef.current.update({
            time: nowBucket as Time,
            open: realtimePrice, high: realtimePrice, low: realtimePrice, close: realtimePrice,
          })
          if (volumeSeriesRef.current) {
            volumeSeriesRef.current.update({
              time: nowBucket as Time,
              value: realtimeVolume ?? 0,
              color: 'rgba(239, 83, 80, 0.3)',
            })
          }
        } else {
          // 항상 현재 캔들의 time을 사용 (KST/UTC 차이로 인한 updateTime < last.time 오류 방지)
          candleSeriesRef.current.update({
            time: last.time as Time,
            open: last.open,
            high: Math.max(last.high, realtimePrice),
            low: Math.min(last.low, realtimePrice),
            close: realtimePrice,
          })
          if (volumeSeriesRef.current) {
            const volData = volumeSeriesRef.current.data()
            const lastVol = volData[volData.length - 1]
            const prevValue = lastVol && 'value' in lastVol ? lastVol.value : 0
            volumeSeriesRef.current.update({
              time: last.time as Time,
              value: prevValue + (realtimeVolume ?? 0),
              color: realtimePrice >= last.open ? 'rgba(239, 83, 80, 0.3)' : 'rgba(25, 118, 210, 0.3)',
            })
          }
        }
      }
    }

    setHeaderData(prev => {
      if (!prev) return prev
      const prevClose = prevCloseRef.current
      const changeAmount = prevClose ? realtimePrice - prevClose : prev.changeAmount
      const changeRate = typeof realtimeChangeRate === 'number'
        ? realtimeChangeRate
        : (prevClose ? (changeAmount / prevClose) * 100 : prev.changeRate)
      return {
        ...prev,
        currentPrice: realtimePrice,
        changeAmount,
        changeRate,
        highPrice: Math.max(prev.highPrice, realtimePrice),
        lowPrice: prev.lowPrice > 0 ? Math.min(prev.lowPrice, realtimePrice) : realtimePrice,
      }
    })
  }, [realtimePrice, realtimeChangeRate, realtimeVolume, period, stockCode])

  const changeColor = headerData && headerData.changeRate >= 0 ? '#ef5350' : '#1976d2'
  const changeSign = headerData && headerData.changeRate >= 0 ? '+' : ''

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0, background: '#0d1117' }}>
      {/* Header */}
      <div style={{ padding: '12px 16px', display: 'flex', gap: '16px', alignItems: 'baseline', flexWrap: 'wrap', borderBottom: '1px solid #1e2533' }}>
        <span style={{ color: '#d1d4dc', fontWeight: 700, fontSize: '1rem' }}>{stockName}</span>
        {headerData && (
          <>
            <span style={{ color: changeColor, fontFamily: 'monospace', fontSize: '1.1rem', fontWeight: 700 }}>
              {headerData.currentPrice.toLocaleString('ko-KR')}
            </span>
            <span style={{ color: changeColor, fontFamily: 'monospace', fontSize: '0.85rem' }}>
              {changeSign}{headerData.changeRate.toFixed(2)}%
            </span>
            <span style={{ color: '#787b86', fontFamily: 'monospace', fontSize: '0.8rem' }}>
              {t('chart.open')} {headerData.openPrice.toLocaleString('ko-KR')}
            </span>
            <span style={{ color: '#787b86', fontFamily: 'monospace', fontSize: '0.8rem' }}>
              {t('chart.high')} {headerData.highPrice.toLocaleString('ko-KR')}
            </span>
            <span style={{ color: '#787b86', fontFamily: 'monospace', fontSize: '0.8rem' }}>
              {t('chart.low')} {headerData.lowPrice.toLocaleString('ko-KR')}
            </span>
          </>
        )}
      </div>

      {/* 탭 */}
      <div style={{ display: 'flex', gap: '4px', padding: '8px 16px', borderBottom: '1px solid #1e2533' }}>
        {PERIOD_TABS.map(tab => (
          <button key={tab.code} onClick={() => setPeriod(tab.code)}
            style={{
              padding: '4px 12px', borderRadius: '4px', border: 'none', cursor: 'pointer',
              fontSize: '0.8rem', fontWeight: period === tab.code ? 600 : 400,
              background: period === tab.code ? '#1e2533' : 'transparent',
              color: period === tab.code ? '#d1d4dc' : '#787b86',
            }}>
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      {/* 차트 */}
      <div style={{ position: 'relative', flex: 1 }}>
        {loading && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(13, 17, 23, 0.8)', zIndex: 10 }}>
            <div style={{ width: 32, height: 32, border: '3px solid #1e2533', borderTopColor: '#3b82f6', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
          </div>
        )}
        {error && !loading && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#787b86', fontSize: '0.9rem' }}>
            {error}
          </div>
        )}
        <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
      </div>
    </div>
  )
}