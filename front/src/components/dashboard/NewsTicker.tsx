'use client'

import { useEffect, useState } from 'react'
import type { NewsTickerDto } from '@/types/news'
import { getNewsTicker } from '@/lib/api/news'

const SENTIMENT_COLOR: Record<string, string> = {
  GOOD: '#22c55e',
  BAD: '#ef4444',
  NEUTRAL: '#6b7280',
}

const SENTIMENT_LABEL: Record<string, string> = {
  GOOD: '🟢',
  BAD: '🔴',
  NEUTRAL: '⚪',
}

export default function NewsTicker() {
  const [newsList, setNewsList] = useState<NewsTickerDto[]>([])

  useEffect(() => {
    getNewsTicker().then(setNewsList).catch(() => {})

    const interval = setInterval(() => {
      getNewsTicker().then(setNewsList).catch(() => {})
    }, 300_000)
    return () => clearInterval(interval)
  }, [])

  if (newsList.length === 0) return null

  return (
    <div
      style={{
        width: '100%',
        overflow: 'hidden',
        background: '#161b22',
        borderBottom: '1px solid #30363d',
        padding: '6px 0',
      }}
    >
      <div
        style={{
          display: 'flex',
          animation: 'scroll-left 13s linear infinite',
          whiteSpace: 'nowrap',
        }}
      >
        {[...newsList, ...newsList].map((item, i) => (
          <span
            key={`${item.stockCode}-${i}`}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              marginRight: 32,
              fontSize: 13,
              color: '#c9d1d9',
            }}
          >
            <span style={{ fontWeight: 600, color: '#58a6ff' }}>{item.stockName}</span>
            <span style={{ color: SENTIMENT_COLOR[item.sentiment] }}>
              {SENTIMENT_LABEL[item.sentiment]} {item.aiScore}
            </span>
            <a
              href={item.newsUrl}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                color: '#c9d1d9',
                textDecoration: 'none',
                cursor: 'pointer',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.color = '#58a6ff')}
              onMouseLeave={(e) => (e.currentTarget.style.color = '#c9d1d9')}
            >
              {item.title}
            </a>
          </span>
        ))}
      </div>
      <style jsx>{`
        @keyframes scroll-left {
          0% {
            transform: translateX(0);
          }
          100% {
            transform: translateX(-50%);
          }
        }
      `}</style>
    </div>
  )
}