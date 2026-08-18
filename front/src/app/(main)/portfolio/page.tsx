'use client'

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getAssets } from '@/lib/api/assets'
import { createCondition } from '@/lib/api/conditions'
import type { AssetSummaryResponse, HoldingInfo } from '@/types/assets'
import type { TriggerRequest } from '@/types/conditions'

const KRW = new Intl.NumberFormat('ko-KR')

function won(v: number): string { return KRW.format(v) }
function signed(v: number): string {
  if (v > 0) return `+${won(v)}`
  if (v < 0) return `-${won(Math.abs(v))}`
  return '0'
}
function pct(v: number): string {
  if (v > 0) return `+${v.toFixed(2)}`
  return v.toFixed(2)
}

const UP = '#e63740'
const DOWN = '#1d64c4'

export default function PortfolioPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [sellTarget, setSellTarget] = useState<HoldingInfo | null>(null)
  const [tpRate, setTpRate] = useState('')
  const [slRate, setSlRate] = useState('')
  const [trailingRate, setTrailingRate] = useState('')
  const [modalMsg, setModalMsg] = useState<string | null>(null)

  const { data: d, isLoading, isError, error, refetch } = useQuery<AssetSummaryResponse>({
    queryKey: ['assets'],
    queryFn: async () => {
      const res = await getAssets()
      if (!res.success || !res.data) throw new Error(res.message || t('common.error'))
      return res.data
    },
    staleTime: 10_000,
    refetchOnWindowFocus: true,
  })

  const sellMutation = useMutation({
    mutationFn: createCondition,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conditions'] })
      setModalMsg(t('trading.conditionRegistered'))
    },
    onError: (e: Error) => setModalMsg(e.message),
  })

  const openSellModal = (h: HoldingInfo) => {
    setSellTarget(h)
    setTpRate('')
    setSlRate('')
    setTrailingRate('')
    setModalMsg(null)
  }

  const closeSellModal = () => {
    setSellTarget(null)
    setModalMsg(null)
  }

  const submitSell = () => {
    if (!sellTarget) return
    const triggers: TriggerRequest[] = []
    const tp = parseFloat(tpRate)
    const sl = parseFloat(slRate)
    const tr = parseFloat(trailingRate)

    if (!isNaN(tp) && tp !== 0) {
      triggers.push({ triggerType: 'PROFIT_TARGET', baseType: 'AVG_PRICE', compareType: 'ABOVE', targetValue: tp, isRate: true })
    }
    if (!isNaN(sl) && sl !== 0) {
      triggers.push({ triggerType: 'STOP_LOSS', baseType: 'AVG_PRICE', compareType: 'BELOW', targetValue: sl, isRate: true })
    }
    if (!isNaN(tr) && tr !== 0) {
      triggers.push({ triggerType: 'TRAILING_STOP', baseType: 'HIGHEST_PRICE', compareType: 'BELOW', targetValue: tr, isRate: true })
    }

    if (triggers.length === 0) {
      setModalMsg(t('conditions.enterValue'))
      return
    }

    sellMutation.mutate({
      stockCode: sellTarget.stockCode,
      orderType: 'SELL',
      orderQuantity: sellTarget.quantity,
      orderPriceType: 'MARKET',
      conditionLogic: 'OR',
      triggers,
    })
  }

  if (isLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', background: '#0d1117', color: '#8b949e', fontSize: 14 }}>
        {t('common.loading')}
      </div>
    )
  }

  if (isError || !d) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', background: '#0d1117', color: '#8b949e', gap: 16, fontSize: 14 }}>
        <p>{error instanceof Error ? error.message : t('common.error')}</p>
        <button onClick={() => refetch()} style={{ padding: '8px 16px', background: '#238636', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 13, fontWeight: 500 }}>
          {t('common.retry')}
        </button>
      </div>
    )
  }

  const totalAssets = d.walletBalance + d.totalEvaluationAmount
  const isProfit = d.totalProfitLossAmount >= 0
  const accent = isProfit ? UP : DOWN
  const arrow = isProfit ? '▲' : '▼'

  return (
    <div style={{ minHeight: '100vh', background: '#0d1117', color: '#f0f6fc', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <style>{`
        @keyframes fadeUp {
          from { opacity: 0; transform: translateY(16px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .card-fade { animation: fadeUp 0.45s ease both; }
        .hover-glow:hover { box-shadow: 0 0 0 1px rgba(255,255,255,0.08); }
      `}</style>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '24px 28px 0' }}>
        <h1 style={{ fontSize: '1.35rem', fontWeight: 700, margin: 0 }}>{t('portfolio.title')}</h1>
        <button onClick={() => refetch()} style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)', color: '#c9d1d9', borderRadius: 999, padding: '6px 18px', fontSize: 12, fontWeight: 500, cursor: 'pointer' }}>
          {t('common.refresh')}
        </button>
      </div>

      {/* Hero */}
      <div style={{ padding: '20px 28px 28px' }}>
        <div className="card-fade" style={{ background: 'linear-gradient(135deg, #131a26 0%, #0d1117 100%)', border: '1px solid rgba(255,255,255,0.06)', borderRadius: 16, padding: '28px 32px', position: 'relative', overflow: 'hidden' }}>
          <p style={{ fontSize: 12, color: '#8b949e', margin: '0 0 6px' }}>{t('portfolio.totalAssets')}</p>
          <p style={{ fontSize: '2.2rem', fontWeight: 800, margin: '0 0 16px', letterSpacing: -1 }}>₩{won(totalAssets)}</p>
          <div style={{ display: 'flex', gap: 24, fontSize: 14 }}>
            <div>
              <span style={{ color: '#8b949e' }}>{t('portfolio.walletBalance')} </span>
              <span style={{ color: '#c9d1d9', fontWeight: 600 }}>₩{won(d.walletBalance)}</span>
            </div>
            <div>
              <span style={{ color: '#8b949e' }}>{t('portfolio.totalProfitLoss')} </span>
              <span style={{ fontWeight: 700, color: accent }}>{arrow} {signed(d.totalProfitLossAmount)}</span>
              <span style={{ fontSize: 12, color: accent, marginLeft: 6 }}>({pct(d.totalProfitLossRate)}%)</span>
            </div>
          </div>
        </div>
      </div>

      {/* 보유 종목 */}
      <div style={{ padding: '0 28px 40px' }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 16 }}>
          <h2 style={{ fontSize: 13, fontWeight: 600, color: '#8b949e', margin: 0, textTransform: 'uppercase', letterSpacing: 1 }}>{t('portfolio.holdings')}</h2>
          <span style={{ fontSize: 13, color: '#484f58', fontWeight: 500 }}>{d.holdings.length}</span>
        </div>

        {d.holdings.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 0', color: '#484f58', fontSize: 14, whiteSpace: 'pre-line' }}>{t('portfolio.noHoldings')}</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {d.holdings.map((h, i) => {
              const up = h.profitLossAmount >= 0
              const color = up ? UP : DOWN
              const arr = up ? '▲' : '▼'
              return (
                <div key={h.stockCode} className="card-fade hover-glow" style={{ animationDelay: `${i * 0.04}s`, background: '#161b22', border: '1px solid #21262d', borderRadius: 12, padding: '20px 24px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                    <span style={{ fontSize: 15, fontWeight: 600, color: '#f0f6fc' }}>{h.stockName}</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <span style={{ fontSize: 14, fontWeight: 700, color }}>{arr} {pct(h.profitLossRate)}%</span>
                      <button onClick={() => openSellModal(h)} style={{ padding: '6px 14px', fontSize: 12, fontWeight: 600, cursor: 'pointer', borderRadius: 8, border: `1px solid ${DOWN}`, background: 'rgba(29,100,196,0.1)', color: DOWN }}>
                        {t('portfolio.autoSell')}
                      </button>
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 12 }}>
                    <div>
                      <span style={{ fontSize: 11, color: '#8b949e' }}>{t('portfolio.averagePrice')}</span>
                      <p style={{ fontSize: 14, fontWeight: 500, color: '#c9d1d9', margin: '4px 0 0' }}>₩{won(h.averagePrice)}</p>
                    </div>
                    <div>
                      <span style={{ fontSize: 11, color: '#8b949e' }}>{t('portfolio.currentPrice')}</span>
                      <p style={{ fontSize: 14, fontWeight: 500, color: '#c9d1d9', margin: '4px 0 0' }}>₩{won(h.currentPrice)}</p>
                    </div>
                    <div>
                      <span style={{ fontSize: 11, color: '#8b949e' }}>{t('portfolio.evaluationAmount')}</span>
                      <p style={{ fontSize: 14, fontWeight: 500, color: '#c9d1d9', margin: '4px 0 0' }}>₩{won(h.evaluationAmount)}</p>
                    </div>
                    <div>
                      <span style={{ fontSize: 11, color: '#8b949e' }}>{t('portfolio.quantity')} · {t('portfolio.profitLoss')}</span>
                      <p style={{ fontSize: 14, margin: '4px 0 0' }}>
                        <span style={{ color: '#c9d1d9', fontWeight: 500 }}>{h.quantity}{t('portfolio.shares')}</span>
                        <span style={{ color, fontWeight: 700, marginLeft: 10 }}>{signed(h.profitLossAmount)}</span>
                      </p>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* 자동 매도 모달 */}
      {sellTarget && (
        <div onClick={closeSellModal} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200 }}>
          <div onClick={(e) => e.stopPropagation()} style={{ width: 400, background: '#161b22', border: '1px solid #30363d', borderRadius: 12, padding: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>{t('portfolio.autoSellSetup')}</h3>
              <button onClick={closeSellModal} style={{ background: 'transparent', border: 'none', color: '#8b949e', fontSize: 18, cursor: 'pointer' }}>✕</button>
            </div>

            <p style={{ fontSize: 13, color: '#c9d1d9', margin: '0 0 16px' }}>
              {sellTarget.stockName} · {t('portfolio.avgPrice')}: ₩{won(sellTarget.averagePrice)} · {sellTarget.quantity}{t('portfolio.shares')}
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: '#8b949e', display: 'block', marginBottom: 4 }}>{t('portfolio.takeProfitRate')}</label>
                <input type="number" value={tpRate} onChange={(e) => setTpRate(e.target.value)} placeholder="+10" style={inputStyle} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: '#8b949e', display: 'block', marginBottom: 4 }}>{t('portfolio.stopLossRate')}</label>
                <input type="number" value={slRate} onChange={(e) => setSlRate(e.target.value)} placeholder="-5" style={inputStyle} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: '#8b949e', display: 'block', marginBottom: 4 }}>{t('portfolio.trailingStopRate')}</label>
                <input type="number" value={trailingRate} onChange={(e) => setTrailingRate(e.target.value)} placeholder="-3" style={inputStyle} />
              </div>
            </div>

            {modalMsg && <p style={{ fontSize: 12, color: '#58a6ff', margin: '12px 0 0' }}>{modalMsg}</p>}

            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <button onClick={submitSell} disabled={sellMutation.isPending} style={{ flex: 1, padding: '11px', fontSize: 14, fontWeight: 700, cursor: 'pointer', borderRadius: 8, border: 'none', background: DOWN, color: '#fff', opacity: sellMutation.isPending ? 0.6 : 1 }}>
                {sellMutation.isPending ? t('common.processing') : t('portfolio.register')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '9px 12px', borderRadius: 6, border: '1px solid #30363d',
  background: '#0d1117', color: '#f0f6fc', fontSize: 13, outline: 'none', boxSizing: 'border-box',
}