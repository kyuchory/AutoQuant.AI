'use client'

import { useState } from 'react'
import axios from 'axios'
import { useParams, useRouter } from 'next/navigation'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getReport, refreshReport } from '@/lib/api/reports'
import type { ReportDto } from '@/types/reports'
import StockSidebar from '@/components/dashboard/StockSidebar'
import ReportContent from '@/components/reports/ReportContent'

export default function ReportPage() {
  const { t } = useTranslation()
  const params = useParams()
  const router = useRouter()
  const queryClient = useQueryClient()
  const stockCode = (params.stockCode as string) || '005930'

  const {
    data: report,
    isLoading,
  } = useQuery<ReportDto | null>({
    queryKey: ['report', stockCode],
    queryFn: () => getReport(stockCode),
    retry: false,
  })

  const [isGenerating, setIsGenerating] = useState(false)
  const [refreshError, setRefreshError] = useState<string | null>(null)

  const refreshMutation = useMutation({
    mutationFn: () => refreshReport(stockCode),
    onSuccess: () => {
      // 캐시 히트 응답은 createdAt이 항상 null로 내려오므로(ReportService.java) 도착 판정에
      // 쓸 수 없다. 대신 실제 리포트 본문(reportContent)이 바뀌었는지로 판정한다.
      const prevContent = queryClient.getQueryData<ReportDto | null>(['report', stockCode])?.reportContent ?? null

      // RabbitMQ 비동기 생성 완료 후 리포트 재조회 (5초 폴링)
      let attempts = 0
      const interval = setInterval(async () => {
        attempts += 1
        await queryClient.invalidateQueries({ queryKey: ['report', stockCode] })
        const data = queryClient.getQueryData<ReportDto | null>(['report', stockCode])
        const arrived = data && data.reportContent !== prevContent
        // 10회(50초) 동안 대기 후 중단
        if (arrived || attempts >= 10) {
          clearInterval(interval)
          setIsGenerating(false)
          if (!arrived) {
            setRefreshError(t('reports.refreshTimeout'))
          }
        }
      }, 5000)
    },
    onError: (error) => {
      setIsGenerating(false)
      const isRateLimited = axios.isAxiosError(error) && error.response?.status === 429
      setRefreshError(isRateLimited ? t('reports.refreshRateLimited') : t('reports.refreshFailed'))
    },
  })

  const handleRefresh = () => {
    if (isGenerating) return
    setRefreshError(null)
    setIsGenerating(true)
    refreshMutation.mutate()
  }

  const handleSelectStock = (code: string) => {
    router.push(`/reports/${code}`)
  }

  const refreshing = isGenerating

  return (
    <div style={{ display: 'flex', height: '100vh', background: '#0d1117', overflow: 'hidden' }}>
      <StockSidebar
        selectedStockCode={stockCode}
        onSelectStock={handleSelectStock}
      />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          padding: '20px 32px', borderBottom: '1px solid #1e2533', minHeight: 72
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <h1 style={{ fontSize: '1.25rem', fontWeight: 600, color: '#d1d4dc', margin: 0 }}>
              {report?.stockName || stockCode}
            </h1>
            {report?.cacheHit && (
              <span style={{ fontSize: 11, fontWeight: 500, color: '#58a6ff', background: 'rgba(88,166,255,0.1)', padding: '2px 8px', borderRadius: 4 }}>
                {t('reports.cached')}
              </span>
            )}
          </div>
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '8px 18px', borderRadius: 6,
              border: '1px solid #30363d', background: refreshing ? '#161b22' : 'transparent',
              color: refreshing ? '#8b949e' : '#d1d4dc', cursor: refreshing ? 'not-allowed' : 'pointer',
              fontSize: 13, fontWeight: 500, transition: 'all 0.2s'
            }}
            onMouseEnter={(e) => { if (!refreshing) { e.currentTarget.style.background = '#1a2332'; e.currentTarget.style.borderColor = '#58a6ff' } }}
            onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = '#30363d' }}
          >
            {refreshing && (
              <span style={{
                width: 12, height: 12, border: '2px solid #30363d',
                borderTopColor: '#58a6ff', borderRadius: '50%',
                animation: 'spin 0.8s linear infinite', display: 'inline-block'
              }} />
            )}
            {refreshing ? t('reports.generating') : t('reports.refreshReport')}
          </button>
        </div>

        {refreshError && (
          <div style={{
            margin: '12px 32px 0', padding: '10px 16px', borderRadius: 6,
            background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.3)',
            color: '#ef4444', fontSize: 13
          }}>
            {refreshError}
          </div>
        )}

        <div style={{ flex: 1, overflow: 'auto', padding: '24px 32px' }}>
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
          {isLoading ? (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60%' }}>
              <div style={{
                width: 32, height: 32, border: '3px solid #1e2533',
                borderTopColor: '#58a6ff', borderRadius: '50%',
                animation: 'spin 0.8s linear infinite'
              }} />
            </div>
          ) : report ? (
            <ReportContent content={report.reportContent} />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '60%', color: '#8b949e' }}>
              <p style={{ fontSize: 15, marginBottom: 8 }}>{t('reports.noReportYet')}</p>
              <p style={{ fontSize: 13, marginBottom: 20, color: '#484f58' }}>
                {t('reports.clickToGenerate')}
              </p>
              <button
                onClick={handleRefresh}
                disabled={refreshing}
                style={{
                  padding: '8px 18px', borderRadius: 6, border: '1px solid #238636',
                  background: 'transparent', color: '#238636', cursor: 'pointer',
                  fontSize: 13, fontWeight: 500, transition: 'all 0.2s'
                }}
                onMouseEnter={(e) => { if (!refreshing) { e.currentTarget.style.background = '#238636'; e.currentTarget.style.color = '#fff' } }}
                onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#238636' }}
              >
                {refreshing ? t('reports.generating') : t('reports.generateReport')}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}