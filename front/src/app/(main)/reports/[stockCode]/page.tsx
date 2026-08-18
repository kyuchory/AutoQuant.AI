'use client'

import { useParams, useRouter } from 'next/navigation'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format, parseISO } from 'date-fns'
import { ko, enUS } from 'date-fns/locale'
import { getReport, refreshReport } from '@/lib/api/reports'
import type { ReportDto } from '@/types/reports'
import StockSidebar from '@/components/dashboard/StockSidebar'

interface ReportData {
  title: string
  recent: string
  opinion: string
  avgScore?: number
  good?: number
  bad?: number
  neutral?: number
  createdAt?: string
}

function parseReportContent(content: string): ReportData | null {
  try {
    let cleaned = content.trim()
    if (cleaned.startsWith('```')) {
      cleaned = cleaned.replace(/(?:```json\s*|```)/g, '').trim()
    }
    return JSON.parse(cleaned) as ReportData
  } catch {
    return null
  }
}

function getScoreColor(score: number): string {
  if (score >= 80) return '#22c55e'
  if (score >= 50) return '#6b7280'
  return '#ef4444'
}

function formatDateSafe(dateStr: string, language: string): string {
  try {
    const isKo = language.startsWith('ko')
    const locale = isKo ? ko : enUS
    const pattern = isKo ? 'yyyy년 M월 d일 a h:mm' : 'MMM d, yyyy h:mm a'
    return format(parseISO(dateStr), pattern, { locale })
  } catch {
    return dateStr
  }
}

function ReportContent({ content }: { content: string }) {
  const { t, i18n } = useTranslation()
  const data = parseReportContent(content)

  if (!data) {
    return (
      <div style={{
        background: '#161b22', border: '1px solid #1e2533', borderRadius: 8,
        padding: '28px 32px', color: '#c9d1d9', lineHeight: 1.8, fontSize: 14,
        whiteSpace: 'pre-wrap', fontFamily: 'system-ui, -apple-system, sans-serif'
      }}>
        {content}
      </div>
    )
  }

  const hasScores = data.avgScore != null

  return (
    <div style={{
      background: '#161b22', border: '1px solid #1e2533', borderRadius: 8,
      padding: '32px', color: '#c9d1d9', fontFamily: 'system-ui, -apple-system, sans-serif'
    }}>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginBottom: 24, paddingBottom: 16, borderBottom: '1px solid #30363d'
      }}>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#f0f6fc', margin: 0 }}>
          {data.title}
        </h2>
        {hasScores && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              background: getScoreColor(data.avgScore!) + '20',
              border: `1px solid ${getScoreColor(data.avgScore!)}40`,
              color: getScoreColor(data.avgScore!),
              borderRadius: 20, padding: '4px 14px', fontSize: 14, fontWeight: 700
            }}>
              ● {data.avgScore}점
            </span>
            <div style={{ display: 'flex', gap: 6, fontSize: 12 }}>
              {data.good! > 0 && (
                <span style={{ background: 'rgba(34,197,94,0.15)', color: '#22c55e', borderRadius: 4, padding: '2px 8px', fontWeight: 500 }}>
                  🟢 {t('reports.sentimentGood')} {data.good}
                </span>
              )}
              {data.bad! > 0 && (
                <span style={{ background: 'rgba(239,68,68,0.15)', color: '#ef4444', borderRadius: 4, padding: '2px 8px', fontWeight: 500 }}>
                  🔴 {t('reports.sentimentBad')} {data.bad}
                </span>
              )}
              {data.neutral! > 0 && (
                <span style={{ background: 'rgba(107,114,128,0.15)', color: '#6b7280', borderRadius: 4, padding: '2px 8px', fontWeight: 500 }}>
                  ⚪ {t('reports.sentimentNeutral')} {data.neutral}
                </span>
              )}
            </div>
          </div>
        )}
      </div>
      {data.createdAt && (
        <div style={{ marginBottom: 24 }}>
          <span style={{ fontSize: 12, color: '#8b949e' }}>
            {formatDateSafe(data.createdAt, i18n.language)}
          </span>
        </div>
      )}
      <div style={{ marginBottom: 24 }}>
        <h3 style={{ fontSize: '0.85rem', fontWeight: 600, color: '#58a6ff', margin: '0 0 10px 0', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
          {t('reports.recentNews')}
        </h3>
        <p style={{ fontSize: 14, lineHeight: 1.8, color: '#c9d1d9', margin: 0 }}>
          {data.recent}
        </p>
      </div>
      <div style={{ background: 'rgba(88,166,255,0.05)', border: '1px solid rgba(88,166,255,0.15)', borderRadius: 8, padding: '20px 24px' }}>
        <h3 style={{ fontSize: '0.85rem', fontWeight: 600, color: '#58a6ff', margin: '0 0 10px 0', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
          {t('reports.opinion')}
        </h3>
        <p style={{ fontSize: 14, lineHeight: 1.8, color: '#c9d1d9', margin: 0 }}>
          {data.opinion}
        </p>
      </div>
      <div style={{ marginTop: 24, background: 'rgba(210,153,34,0.06)', border: '1px solid rgba(210,153,34,0.15)', borderRadius: 6, padding: '10px 16px' }}>
        <p style={{ fontSize: 11, color: '#8b949e', margin: 0, lineHeight: 1.6 }}>
          {t('reports.disclaimer')}
        </p>
      </div>
    </div>
  )
}

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

  const refreshMutation = useMutation({
    mutationFn: () => refreshReport(stockCode),
    onSuccess: () => {
      // RabbitMQ 비동기 생성 완료 후 리포트 재조회 (5초 폴링)
      let attempts = 0
      const interval = setInterval(async () => {
        attempts += 1
        await queryClient.invalidateQueries({ queryKey: ['report', stockCode] })
        const data = queryClient.getQueryData<ReportDto | null>(['report', stockCode])
        // 10회(50초) 동안 대기 후 중단
        if (data || attempts >= 10) {
          clearInterval(interval)
        }
      }, 5000)
    },
  })

  const handleRefresh = () => {
    refreshMutation.mutate()
  }

  const handleSelectStock = (code: string) => {
    router.push(`/reports/${code}`)
  }

  const refreshing = refreshMutation.isPending

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
              padding: '8px 18px', borderRadius: 6,
              border: '1px solid #30363d', background: refreshing ? '#161b22' : 'transparent',
              color: refreshing ? '#8b949e' : '#d1d4dc', cursor: refreshing ? 'not-allowed' : 'pointer',
              fontSize: 13, fontWeight: 500, transition: 'all 0.2s'
            }}
            onMouseEnter={(e) => { if (!refreshing) { e.currentTarget.style.background = '#1a2332'; e.currentTarget.style.borderColor = '#58a6ff' } }}
            onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = '#30363d' }}
          >
            {refreshing ? t('reports.generating') : t('reports.refreshReport')}
          </button>
        </div>

        <div style={{ flex: 1, overflow: 'auto', padding: '24px 32px' }}>
          {isLoading ? (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60%' }}>
              <div style={{
                width: 32, height: 32, border: '3px solid #1e2533',
                borderTopColor: '#58a6ff', borderRadius: '50%',
                animation: 'spin 0.8s linear infinite'
              }} />
              <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
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