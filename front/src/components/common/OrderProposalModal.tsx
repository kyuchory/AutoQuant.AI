'use client'

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useOrderProposalStore } from '@/lib/store/orderProposalStore'
import { createOrder } from '@/lib/api/assets'
import type { TFunction } from 'i18next'

const UP = '#e63740'
const DOWN = '#1d64c4'
const AI_COLOR = '#a855f7'

/** 기사 발행 시간 상대시간(Relative Time) 포맷팅 */
function formatTimeAgo(t: TFunction, isoString?: string): string {
  if (!isoString) return ''
  try {
    const pubDate = new Date(isoString)
    const now = new Date()
    const diffMs = now.getTime() - pubDate.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    const diffHour = Math.floor(diffMin / 60)

    const timeStr = pubDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

    if (diffMin < 1) return t('orderProposal.timeJustNow', { time: timeStr })
    if (diffMin < 60) return t('orderProposal.timeMinutesAgo', { count: diffMin, time: timeStr })
    if (diffHour < 24) return t('orderProposal.timeHoursAgo', { count: diffHour, time: timeStr })
    return t('orderProposal.timeDateAgo', { month: pubDate.getMonth() + 1, day: pubDate.getDate(), time: timeStr })
  } catch {
    return isoString
  }
}

export default function OrderProposalModal() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const proposal = useOrderProposalStore((s) => s.proposal)
  const isOpen = useOrderProposalStore((s) => s.isOpen)
  const closeProposal = useOrderProposalStore((s) => s.closeProposal)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const orderMutation = useMutation({
    mutationFn: createOrder,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] })
      queryClient.invalidateQueries({ queryKey: ['histories'] })
      closeProposal()
    },
    onError: (e: Error) => {
      setErrorMsg(e.message || t('orderProposal.orderFailed'))
    },
  })

  if (!isOpen || !proposal) return null

  const isBuy = proposal.orderType === 'BUY'
  const accentColor = isBuy ? UP : DOWN
  const totalEstimatedAmount = Math.round(proposal.currentPrice * proposal.orderQuantity)
  const isAiProposal = proposal.aiScore != null

  const handleExecuteOrder = () => {
    setErrorMsg(null)
    const isLimit = proposal.orderPriceType === 'LIMIT'
    orderMutation.mutate({
      stockCode: proposal.stockCode,
      orderType: proposal.orderType,
      quantity: proposal.orderQuantity,
      ordDvsn: isLimit ? '00' : '01',
      // AI가 제안한 지정가를 실제 주문에도 실어 보낸다 — 이전엔 이 필드가 빠져서
      // 화면엔 "지정가 ₩X"로 보여주고 실제로는 시장가로 체결됐다.
      ...(isLimit && proposal.limitPrice != null ? { price: proposal.limitPrice } : {}),
    })
  }

  return (
    <div
      onClick={closeProposal}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.75)',
        backdropFilter: 'blur(6px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: 16,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%',
          maxWidth: 520,
          background: 'linear-gradient(145deg, #161b22, #0d1117)',
          border: `1px solid ${isAiProposal ? 'rgba(168, 85, 247, 0.4)' : '#30363d'}`,
          boxShadow: isAiProposal
            ? '0 12px 40px rgba(168, 85, 247, 0.25)'
            : '0 12px 40px rgba(0, 0, 0, 0.6)',
          borderRadius: 16,
          padding: 24,
          color: '#f0f6fc',
          animation: 'fadeIn 0.2s ease-out',
        }}
      >
        {/* ── 헤더 ── */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              {isAiProposal && (
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 700,
                    padding: '3px 8px',
                    borderRadius: 6,
                    background: 'rgba(168, 85, 247, 0.2)',
                    color: '#d8b4fe',
                    border: '1px solid rgba(168, 85, 247, 0.4)',
                  }}
                >
                  {t('orderProposal.aiBadge')}
                </span>
              )}
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 700,
                  padding: '3px 8px',
                  borderRadius: 6,
                  background: isBuy ? 'rgba(230, 55, 64, 0.15)' : 'rgba(29, 100, 196, 0.15)',
                  color: accentColor,
                  border: `1px solid ${isBuy ? 'rgba(230, 55, 64, 0.3)' : 'rgba(29, 100, 196, 0.3)'}`,
                }}
              >
                {isBuy ? t('orderProposal.signalBuy') : t('orderProposal.signalSell')}
              </span>
            </div>
            <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0 }}>
              {proposal.stockName}{' '}
              <span style={{ fontSize: 13, color: '#8b949e', fontWeight: 400 }}>({proposal.stockCode})</span>
            </h2>
          </div>
          <button
            onClick={closeProposal}
            style={{
              background: 'transparent',
              border: 'none',
              color: '#8b949e',
              fontSize: 20,
              cursor: 'pointer',
              padding: 4,
            }}
          >
            ✕
          </button>
        </div>

        {/* ── 발동 사유 ── */}
        <div
          style={{
            background: '#161b22',
            border: '1px solid #21262d',
            borderRadius: 10,
            padding: '10px 14px',
            marginBottom: 16,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            fontSize: 13,
          }}
        >
          <span style={{ color: '#8b949e' }}>{t('orderProposal.triggerCondition')}</span>
          <span style={{ fontWeight: 600, color: '#c9d1d9' }}>{proposal.triggerReason}</span>
        </div>

        {/* ── AI 분석 근거 및 기사 박스 ── */}
        {isAiProposal && (
          <div
            style={{
              background: 'rgba(168, 85, 247, 0.06)',
              border: '1px solid rgba(168, 85, 247, 0.25)',
              borderRadius: 12,
              padding: 16,
              marginBottom: 16,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: '#d8b4fe' }}>{t('orderProposal.aiAnalysisTitle')}</span>
              {proposal.aiScore != null && (
                <span
                  style={{
                    fontSize: 12,
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: 12,
                    background: proposal.aiScore >= 60 ? 'rgba(35, 134, 54, 0.2)' : 'rgba(230, 55, 64, 0.2)',
                    color: proposal.aiScore >= 60 ? '#3fb950' : '#f85149',
                    border: `1px solid ${proposal.aiScore >= 60 ? 'rgba(35, 134, 54, 0.4)' : 'rgba(230, 55, 64, 0.4)'}`,
                  }}
                >
                  {t('orderProposal.aiScoreLabel', {
                    score: proposal.aiScore,
                    sentiment: proposal.aiSentiment || t('orderProposal.analysisComplete'),
                  })}
                </span>
              )}
            </div>

            {proposal.aiReason && (
              <p style={{ fontSize: 13, color: '#f0f6fc', lineHeight: 1.5, margin: '0 0 10px 0', fontWeight: 500 }}>
                &ldquo;{proposal.aiReason}&rdquo;
              </p>
            )}

            {/* 판단 근거 뉴스 카드 */}
            {proposal.newsTitle && (
              <div
                style={{
                  background: '#0d1117',
                  border: '1px solid #30363d',
                  borderRadius: 8,
                  padding: 12,
                  marginTop: 8,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8, marginBottom: 4 }}>
                  <span style={{ fontSize: 13, fontWeight: 600, color: '#58a6ff', flex: 1 }}>
                    📰 {proposal.newsTitle}
                  </span>
                  {proposal.newsPublishedAt && (
                    <span style={{ fontSize: 11, color: '#f0883e', whiteSpace: 'nowrap', fontWeight: 600 }}>
                      ⏱️ {formatTimeAgo(t, proposal.newsPublishedAt)}
                    </span>
                  )}
                </div>

                {proposal.newsSummary && (
                  <p style={{ fontSize: 12, color: '#8b949e', margin: '4px 0 8px 0', lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                    {proposal.newsSummary}
                  </p>
                )}

                {proposal.newsUrl && (
                  <a
                    href={proposal.newsUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ fontSize: 11, color: '#8b949e', textDecoration: 'underline' }}
                  >
                    {t('orderProposal.newsSourceLink')}
                  </a>
                )}
              </div>
            )}
          </div>
        )}

        {/* ── 제안 주문 내용 카드 ── */}
        <div
          style={{
            background: '#161b22',
            border: '1px solid #30363d',
            borderRadius: 12,
            padding: 16,
            marginBottom: 20,
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
            <div>
              <span style={{ fontSize: 11, color: '#8b949e' }}>{t('trading.currentPrice')}</span>
              <p style={{ fontSize: 16, fontWeight: 700, margin: '2px 0 0', color: '#f0f6fc' }}>
                ₩{proposal.currentPrice?.toLocaleString()}
              </p>
            </div>
            <div>
              <span style={{ fontSize: 11, color: '#8b949e' }}>{t('orderProposal.proposedOrder')}</span>
              <p style={{ fontSize: 16, fontWeight: 700, margin: '2px 0 0', color: accentColor }}>
                {isBuy ? t('trading.buy') : t('trading.sell')} {proposal.orderQuantity}{t('trading.shares')} (
                {proposal.orderPriceType === 'LIMIT'
                  ? `${t('trading.limitPrice')} ₩${proposal.limitPrice?.toLocaleString()}`
                  : t('trading.marketPrice')}
                )
              </p>
            </div>
          </div>

          <div style={{ borderTop: '1px solid #21262d', paddingTop: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 12, color: '#8b949e' }}>{t('orderProposal.estimatedTotal')}</span>
            <span style={{ fontSize: 16, fontWeight: 800, color: '#f0f6fc' }}>
              ₩{totalEstimatedAmount.toLocaleString()}
            </span>
          </div>
        </div>

        {errorMsg && (
          <p style={{ fontSize: 12, color: '#f85149', margin: '0 0 12px 0', textAlign: 'center' }}>
            {errorMsg}
          </p>
        )}

        {/* ── 액션 버튼 ── */}
        <div style={{ display: 'flex', gap: 10 }}>
          <button
            onClick={closeProposal}
            disabled={orderMutation.isPending}
            style={{
              flex: 1,
              padding: '12px',
              fontSize: 13,
              fontWeight: 600,
              borderRadius: 8,
              border: '1px solid #30363d',
              background: 'transparent',
              color: '#8b949e',
              cursor: 'pointer',
            }}
          >
            {t('orderProposal.skip')}
          </button>
          <button
            onClick={handleExecuteOrder}
            disabled={orderMutation.isPending}
            style={{
              flex: 2,
              padding: '12px',
              fontSize: 14,
              fontWeight: 700,
              borderRadius: 8,
              border: 'none',
              background: isBuy ? 'linear-gradient(135deg, #e63740, #ff5c65)' : 'linear-gradient(135deg, #1d64c4, #3b82f6)',
              color: '#ffffff',
              cursor: orderMutation.isPending ? 'not-allowed' : 'pointer',
              opacity: orderMutation.isPending ? 0.7 : 1,
              boxShadow: isBuy ? '0 4px 14px rgba(230, 55, 64, 0.4)' : '0 4px 14px rgba(29, 100, 196, 0.4)',
            }}
          >
            {orderMutation.isPending
              ? t('orderProposal.executing')
              : isBuy ? t('orderProposal.executeBuyNow') : t('orderProposal.executeSellNow')}
          </button>
        </div>
      </div>
    </div>
  )
}
