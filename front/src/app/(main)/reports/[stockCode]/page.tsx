'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { useTranslation } from 'react-i18next'
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
    // AI 응답에서 Markdown 코드블록 제거 후 JSON 파싱
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
  if (score >= 80) return '#22c55e'   // GOOD — NewsTicker와 동일
  if (score >= 50) return '#6b7280'   // NEUTRAL — NewsTicker와 동일
  return '#ef4444'                     // BAD — NewsTicker와 동일
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
    // JSON 파싱 실패 시 기존 방식으로 fallback
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
      {/* 제목 + 점수 뱃지 */}
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginBottom: 24, paddingBottom: 16, borderBottom: '1px solid #30363d'
      }}>
        <h2 style={{
          fontSize: '1.5rem', fontWeight: 700, color: '#f0f6fc', margin: 0
        }}>
          {data.title}
        </h2>
        {hasScores && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {/* 점수 뱃지 */}
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              background: getScoreColor(data.avgScore!) + '20',
              border: `1px solid ${getScoreColor(data.avgScore!)}40`,
              color: getScoreColor(data.avgScore!),
              borderRadius: 20, padding: '4px 14px',
              fontSize: 14, fontWeight: 700
            }}>
              ● {data.avgScore}점
            </span>
            {/* 감성 태그 — NewsTicker와 동일한 스타일 */}
            <div style={{ display: 'flex', gap: 6, fontSize: 12 }}>
              {data.good! > 0 && (
                <span style={{
                  background: 'rgba(34,197,94,0.15)', color: '#22c55e',
                  borderRadius: 4, padding: '2px 8px', fontWeight: 500
                }}>
                  🟢 {t('reports.sentimentGood')} {data.good}
                </span>
              )}
              {data.bad! > 0 && (
                <span style={{
                  background: 'rgba(239,68,68,0.15)', color: '#ef4444',
                  borderRadius: 4, padding: '2px 8px', fontWeight: 500
                }}>
                  🔴 {t('reports.sentimentBad')} {data.bad}
                </span>
              )}
              {data.neutral! > 0 && (
                <span style={{
                  background: 'rgba(107,114,128,0.15)', color: '#6b7280',
                  borderRadius: 4, padding: '2px 8px', fontWeight: 500
                }}>
                  ⚪ {t('reports.sentimentNeutral')} {data.neutral}
                </span>
              )}
            </div>
          </div>
        )}
      </div>
      {/* 생성 일시 */}
      {data.createdAt && (
        <div style={{ marginBottom: 24 }}>
          <span style={{ fontSize: 12, color: '#8b949e' }}>
            {formatDateSafe(data.createdAt, i18n.language)}
          </span>
        </div>
      )}

      {/* 최근 뉴스 요약 */}
      <div style={{ marginBottom: 24 }}>
        <h3 style={{
          fontSize: '0.85rem', fontWeight: 600, color: '#58a6ff',
          margin: '0 0 10px 0', textTransform: 'uppercase', letterSpacing: '0.5px'
        }}>
          {t('reports.recentNews')}
        </h3>
        <p style={{
          fontSize: 14, lineHeight: 1.8, color: '#c9d1d9', margin: 0
        }}>
          {data.recent}
        </p>
      </div>

      {/* 종합 의견 */}
      <div style={{
        background: 'rgba(88,166,255,0.05)', border: '1px solid rgba(88,166,255,0.15)',
        borderRadius: 8, padding: '20px 24px'
      }}>
        <h3 style={{
          fontSize: '0.85rem', fontWeight: 600, color: '#58a6ff',
          margin: '0 0 10px 0', textTransform: 'uppercase', letterSpacing: '0.5px'
        }}>
          {t('reports.opinion')}
        </h3>
        <p style={{
          fontSize: 14, lineHeight: 1.8, color: '#c9d1d9', margin: 0
        }}>
          {data.opinion}
        </p>
      </div>

      {/* AI 경고문 */}
      <div style={{
        marginTop: 24,
        background: 'rgba(210,153,34,0.06)',
        border: '1px solid rgba(210,153,34,0.15)',
        borderRadius: 6, padding: '10px 16px'
      }}>
        <p style={{
          fontSize: 11, color: '#8b949e', margin: 0, lineHeight: 1.6
        }}>
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
  const stockCode = (params.stockCode as string) || '005930'

  const [report, setReport] = useState<ReportDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    getReport(stockCode)
      .then(setReport)
      .catch(() => setReport(null))
      .finally(() => setLoading(false))
  }, [stockCode])

  const handleRefresh = async () => {
    setRefreshing(true)
    try {
      await refreshReport(stockCode)
      setTimeout(async () => {
        const data = await getReport(stockCode)
        setReport(data)
        setRefreshing(false)
      }, 5000)
    } catch {
      setRefreshing(false)
    }
  }

  const handleSelectStock = (code: string) => {
    router.push(`/reports/${code}`)
  }

  return (
    <div style={{ display: 'flex', height: '100vh', background: '#0d1117', overflow: 'hidden' }}>
      <StockSidebar
        selectedStockCode={stockCode}
        onSelectStock={handleSelectStock}
      />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* Header */}
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          padding: '20px 32px', borderBottom: '1px solid #1e2533', minHeight: 72
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <h1 style={{ fontSize: '1.25rem', fontWeight: 600, color: '#d1d4dc', margin: 0 }}>
              {report?.stockName || stockCode}
            </h1>
            {report?.cacheHit && (
              <span style={{
                fontSize: 11, fontWeight: 500, color: '#58a6ff',
                background: 'rgba(88,166,255,0.1)', padding: '2px 8px', borderRadius: 4
              }}>
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

        {/* Content */}
        <div style={{ flex: 1, overflow: 'auto', padding: '24px 32px' }}>
          {loading ? (
            <div style={{
              display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60%'
            }}>
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
            <div style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center',
              justifyContent: 'center', height: '60%', color: '#8b949e'
            }}>
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