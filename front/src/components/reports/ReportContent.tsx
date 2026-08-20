'use client'

import { useTranslation } from 'react-i18next'
import { format, parseISO } from 'date-fns'
import { ko, enUS } from 'date-fns/locale'

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

/** AI 리포트 JSON 뷰어: 점수 배지, 감성 태그, 생성일시, AI 경고문 (docs/frontend.md §components/reports) */
export default function ReportContent({ content }: { content: string }) {
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
