'use client'

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getStocks } from '@/lib/api/stocks'
import { getConditions, createCondition, updateCondition, updateConditionActive, deleteCondition } from '@/lib/api/conditions'
import { getHistories } from '@/lib/api/assets'
import type { TriggerType, BaseType, CompareType, ExecutionMode } from '@/types/conditions'

// ─────────────────────────────────────────────────────────────
// 타입
// ─────────────────────────────────────────────────────────────
interface DraftTrigger {
  triggerType: TriggerType
  baseType: BaseType
  compareType: CompareType
  targetValue: string
  isRate: boolean
}

interface DraftAction {
  orderType: 'BUY' | 'SELL'
  orderQuantity: string
  orderPriceType: 'MARKET' | 'LIMIT'
  limitPrice: string
}

type SinglePresetKind = 'PRICE' | 'PROFIT_TARGET' | 'STOP_LOSS' | 'TRAILING_STOP' | 'AI_SCORE'

// ─────────────────────────────────────────────────────────────
// 아이콘 (이모지 대체용 SVG)
// ─────────────────────────────────────────────────────────────
type IconProps = { size?: number; style?: React.CSSProperties }

function IconZap({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
    </svg>
  )
}

function IconSparkles({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" style={style}>
      <path d="M12 2l1.8 5.7L19.5 9.5l-5.7 1.8L12 17l-1.8-5.7L4.5 9.5l5.7-1.8L12 2z" />
      <path d="M19 14l0.8 2.2L22 17l-2.2 0.8L19 20l-0.8-2.2L16 17l2.2-0.8L19 14z" />
    </svg>
  )
}

function IconShield({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <path d="M12 2l8 4v6c0 5-3.4 8.5-8 10-4.6-1.5-8-5-8-10V6l8-4z" />
    </svg>
  )
}

function IconBell({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <path d="M6 8a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6" />
      <path d="M10.3 21a1.9 1.9 0 0 0 3.4 0" />
    </svg>
  )
}

function IconRepeat({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <polyline points="17 1 21 5 17 9" />
      <path d="M3 11V9a4 4 0 0 1 4-4h14" />
      <polyline points="7 23 3 19 7 15" />
      <path d="M21 13v2a4 4 0 0 1-4 4H3" />
    </svg>
  )
}

function IconClose({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function IconPlus({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  )
}

function IconEdit({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
    </svg>
  )
}

function IconTrash({ size = 14, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    </svg>
  )
}

function IconArrowUp({ size = 12, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <line x1="12" y1="19" x2="12" y2="5" />
      <polyline points="6 11 12 5 18 11" />
    </svg>
  )
}

function IconArrowDown({ size = 12, style }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <line x1="12" y1="5" x2="12" y2="19" />
      <polyline points="18 13 12 19 6 13" />
    </svg>
  )
}

// ─────────────────────────────────────────────────────────────
// 트리거 타입별 기본 메타데이터
// ─────────────────────────────────────────────────────────────
const TRIGGER_PRESETS: Record<SinglePresetKind, { triggerType: TriggerType; baseType: BaseType; compareType: CompareType; isRate: boolean; orderType: 'BUY' | 'SELL'; targetLabel: string }> = {
  PRICE: { triggerType: 'PRICE', baseType: 'CURRENT_PRICE', compareType: 'ABOVE', isRate: false, orderType: 'BUY', targetLabel: 'conditions.triggerPricePlaceholder' },
  PROFIT_TARGET: { triggerType: 'PROFIT_TARGET', baseType: 'AVG_PRICE', compareType: 'ABOVE', isRate: true, orderType: 'SELL', targetLabel: 'conditions.triggerRatePlaceholder' },
  STOP_LOSS: { triggerType: 'STOP_LOSS', baseType: 'AVG_PRICE', compareType: 'BELOW', isRate: true, orderType: 'SELL', targetLabel: 'conditions.triggerRatePlaceholder' },
  TRAILING_STOP: { triggerType: 'TRAILING_STOP', baseType: 'HIGHEST_PRICE', compareType: 'BELOW', isRate: true, orderType: 'SELL', targetLabel: 'conditions.triggerRatePlaceholder' },
  AI_SCORE: { triggerType: 'AI_SCORE', baseType: 'AI_SCORE', compareType: 'ABOVE', isRate: false, orderType: 'BUY', targetLabel: 'conditions.aiScorePlaceholder' },
}

const TRIGGER_TYPE_KEYS: Record<TriggerType, string> = {
  PRICE: 'conditions.triggerPrice',
  PROFIT_TARGET: 'conditions.triggerProfitLoss',
  STOP_LOSS: 'conditions.triggerProfitLoss',
  TRAILING_STOP: 'conditions.triggerTrailing',
  AI_SCORE: 'conditions.triggerAiScore',
}

const UP = '#e63740'
const DOWN = '#1d64c4'
const AI_COLOR = '#a855f7'
const BG = '#0d1117'
const CARD = '#161b22'
const BORDER = '#21262d'
const BORDER_STRONG = '#30363d'
const TEXT = '#f0f6fc'
const MUTED = '#8b949e'

// ─────────────────────────────────────────────────────────────
// 페이지
// ─────────────────────────────────────────────────────────────
export default function ConditionsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [stockCode, setStockCode] = useState('')
  const [triggers, setTriggers] = useState<DraftTrigger[]>([{ triggerType: 'PRICE', baseType: 'CURRENT_PRICE', compareType: 'ABOVE', targetValue: '', isRate: false }])
  const [logic, setLogic] = useState<'AND' | 'OR'>('AND')
  const [action, setAction] = useState<DraftAction>({ orderType: 'BUY', orderQuantity: '1', orderPriceType: 'MARKET', limitPrice: '' })
  const [executionMode, setExecutionMode] = useState<ExecutionMode>('AUTO')
  const [isPersistent, setIsPersistent] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const { data: stocks = [] } = useQuery({ queryKey: ['stocks'], queryFn: getStocks, staleTime: 30000 })
  const { data: conditions = [] } = useQuery({
    queryKey: ['conditions'],
    queryFn: async () => (await getConditions()).data ?? [],
  })
  // queryKey는 OrderProposalModal의 반자동 체결 성공 시 invalidateQueries(['histories'])와 맞춘다.
  const { data: histories = [] } = useQuery({
    queryKey: ['histories'],
    queryFn: async () => (await getHistories({ size: 20 })).data?.content ?? [],
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['conditions'] })

  const createMutation = useMutation({
    mutationFn: createCondition,
    onSuccess: () => { invalidate(); setMessage(t('conditions.created')); resetForm() },
    onError: (e: Error) => setMessage(e.message),
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, req }: { id: number; req: Parameters<typeof updateCondition>[1] }) => updateCondition(id, req),
    onSuccess: () => { invalidate(); setMessage(t('conditions.updated')); setEditingId(null); resetForm() },
    onError: (e: Error) => setMessage(e.message),
  })
  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => updateConditionActive(id, active),
    onSuccess: (_d, vars) => { invalidate(); setMessage(vars.active ? t('conditions.toggledOn') : t('conditions.toggledOff')) },
    onError: (e: Error) => setMessage(e.message),
  })
  const deleteMutation = useMutation({
    mutationFn: deleteCondition,
    onSuccess: () => { invalidate(); setMessage(t('conditions.deleted')) },
    onError: (e: Error) => setMessage(e.message),
  })

  const resetForm = () => {
    setTriggers([{ triggerType: 'PRICE', baseType: 'CURRENT_PRICE', compareType: 'ABOVE', targetValue: '', isRate: false }])
    setLogic('AND')
    setAction({ orderType: 'BUY', orderQuantity: '1', orderPriceType: 'MARKET', limitPrice: '' })
    setExecutionMode('AUTO')
    setIsPersistent(false)
    setStockCode('')
  }

  const buildRequest = () => {
    return {
      stockCode,
      orderType: action.orderType,
      orderQuantity: parseInt(action.orderQuantity, 10) || 1,
      orderPriceType: action.orderPriceType,
      limitPrice: action.orderPriceType === 'LIMIT' && action.limitPrice ? parseFloat(action.limitPrice) : undefined,
      conditionLogic: logic,
      executionMode,
      isPersistent,
      triggers: triggers.map((tr) => ({
        triggerType: tr.triggerType,
        baseType: tr.baseType,
        compareType: tr.compareType,
        targetValue: parseFloat(tr.targetValue) || 0,
        isRate: tr.isRate,
      })),
    }
  }

  const submit = () => {
    setMessage(null)
    if (!stockCode) { setMessage(t('conditions.stockRequired')); return }
    if (triggers.length === 0) { setMessage(t('conditions.triggerRequired')); return }
    for (const tr of triggers) {
      if (!tr.targetValue || isNaN(parseFloat(tr.targetValue))) {
        setMessage(t('conditions.targetValueRequired'))
        return
      }
    }
    const req = buildRequest()
    if (editingId != null) {
      updateMutation.mutate({ id: editingId, req })
    } else {
      createMutation.mutate(req)
    }
  }

  // 프리셋 적용
  const applySinglePreset = (kind: SinglePresetKind) => {
    const meta = TRIGGER_PRESETS[kind]
    setTriggers([{ triggerType: meta.triggerType, baseType: meta.baseType, compareType: meta.compareType, targetValue: '', isRate: meta.isRate }])
    setAction((prev) => ({ ...prev, orderType: meta.orderType }))
  }

  // 복합 AI 프리셋 1: AI 급등 포착 (AI 호재 점수 70점 이상 AND 가격 돌파)
  const applyAiSurgePreset = () => {
    setTriggers([
      { triggerType: 'AI_SCORE', baseType: 'AI_SCORE', compareType: 'ABOVE', targetValue: '70', isRate: false },
      { triggerType: 'PRICE', baseType: 'CURRENT_PRICE', compareType: 'ABOVE', targetValue: '', isRate: false },
    ])
    setLogic('AND')
    setAction((prev) => ({ ...prev, orderType: 'BUY' }))
  }

  // 복합 AI 프리셋 2: AI 급락 방어 (AI 악재 점수 30점 이하 OR 손절선 터치)
  const applyAiDropDefendPreset = () => {
    setTriggers([
      { triggerType: 'AI_SCORE', baseType: 'AI_SCORE', compareType: 'BELOW', targetValue: '30', isRate: false },
      { triggerType: 'STOP_LOSS', baseType: 'AVG_PRICE', compareType: 'BELOW', targetValue: '-3', isRate: true },
    ])
    setLogic('OR')
    setAction((prev) => ({ ...prev, orderType: 'SELL' }))
  }

  // 트리거 추가/수정/삭제
  const addTrigger = () => {
    if (triggers.length >= 5) return
    setTriggers((prev) => [...prev, { triggerType: 'PRICE', baseType: 'CURRENT_PRICE', compareType: 'ABOVE', targetValue: '', isRate: false }])
  }
  const removeTrigger = (idx: number) => {
    if (triggers.length <= 1) return
    setTriggers((prev) => prev.filter((_, i) => i !== idx))
  }
  const updateTrigger = (idx: number, patch: Partial<DraftTrigger>) => {
    setTriggers((prev) => prev.map((t, i) => (i === idx ? { ...t, ...patch } : t)))
  }

  const startEdit = (id: number, c: (typeof conditions)[0]) => {
    setEditingId(id)
    setStockCode(c.stockCode)
    setLogic(c.conditionLogic)
    setExecutionMode(c.executionMode || 'AUTO')
    setIsPersistent(c.isPersistent ?? false)
    setAction({
      orderType: c.orderType,
      orderQuantity: String(c.orderQuantity),
      orderPriceType: c.orderPriceType,
      limitPrice: c.limitPrice != null ? String(c.limitPrice) : '',
    })
    setTriggers(
      c.triggers.map((tr) => ({
        triggerType: tr.triggerType,
        baseType: tr.baseType,
        compareType: tr.compareType,
        targetValue: String(tr.targetValue),
        isRate: tr.isRate,
      }))
    )
  }

  const cancelEdit = () => {
    setEditingId(null)
    resetForm()
  }

  return (
    <div style={{ height: '100%', overflowY: 'auto', padding: '24px 32px 48px', color: TEXT }}>
      <style>{`
        .cond-grid { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 24px; align-items: start; }
        .cond-sidebar { position: sticky; top: 0; }
        @media (max-width: 980px) {
          .cond-grid { grid-template-columns: 1fr; }
          .cond-sidebar { position: static; }
        }
        .chip { transition: border-color 0.15s ease, background 0.15s ease, transform 0.15s ease; }
        .chip:hover { transform: translateY(-1px); }
        .chip:active { transform: translateY(0); }
        .cond-item { transition: border-color 0.15s ease, background 0.15s ease; }
        .cond-item:hover { border-color: ${BORDER_STRONG}; }
      `}</style>

      <div style={{ maxWidth: 1400, margin: '0 auto' }}>
        {/* 헤더 */}
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontSize: 24, fontWeight: 700, margin: '0 0 6px' }}>{t('conditions.title')}</h1>
          <p style={{ fontSize: 13, color: MUTED, margin: 0 }}>{t('conditions.subtitle')}</p>
        </div>

        <div className="cond-grid">
          {/* ── 좌측: 빌더 ── */}
          <div>
            {/* 스마트 프리셋 바 */}
            <div style={{ background: CARD, border: `1px solid ${BORDER_STRONG}`, borderRadius: 14, padding: '16px 20px', marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 12 }}>
                <IconZap size={13} style={{ color: '#58a6ff' }} />
                <span style={{ fontSize: 13, fontWeight: 600, color: '#c9d1d9' }}>{t('conditions.presetBarTitle')}</span>
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                <button className="chip" onClick={() => applySinglePreset('PRICE')} style={presetButtonStyle}>{t('conditions.presetPrice')}</button>
                <button className="chip" onClick={() => applySinglePreset('PROFIT_TARGET')} style={presetButtonStyle}>{t('conditions.presetProfit')}</button>
                <button className="chip" onClick={() => applySinglePreset('STOP_LOSS')} style={presetButtonStyle}>{t('conditions.presetStop')}</button>
                <button className="chip" onClick={() => applySinglePreset('TRAILING_STOP')} style={presetButtonStyle}>{t('conditions.presetTrailing')}</button>
                <button className="chip" onClick={() => applySinglePreset('AI_SCORE')} style={aiPresetButtonStyle}>
                  <IconSparkles size={12} />{t('conditions.presetAiScore')}
                </button>
                <button className="chip" onClick={applyAiSurgePreset} style={aiPresetButtonStyle}>
                  <IconSparkles size={12} /><IconZap size={12} />{t('conditions.presetAiSurge')}
                </button>
                <button className="chip" onClick={applyAiDropDefendPreset} style={aiPresetButtonStyle}>
                  <IconSparkles size={12} /><IconShield size={12} />{t('conditions.presetAiDropDefend')}
                </button>
              </div>
            </div>

            {/* IF-THEN 조건 빌더 */}
            <div style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 14, padding: 20, marginBottom: 24 }}>
              {/* 종목 선택 */}
              <label style={labelStyle}>{t('conditions.targetStock')}</label>
              <select value={stockCode} onChange={(e) => setStockCode(e.target.value)} style={selectStyle}>
                <option value="">{t('conditions.selectStock')}</option>
                {stocks.map((s) => (
                  <option key={s.stockCode} value={s.stockCode}>{s.stockName} ({s.stockCode})</option>
                ))}
              </select>

              {/* IF 블록 (발동 조건들) */}
              <div style={{ background: BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 16, marginBottom: 16 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 9px', borderRadius: 6, background: '#1f6feb', color: '#fff', letterSpacing: 0.5 }}>IF</span>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#c9d1d9' }}>{t('conditions.ifTitle')}</span>
                  </div>

                  {/* 복수 조건 결합 로직 (AND / OR) */}
                  {triggers.length > 1 && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontSize: 12, color: MUTED }}>{t('conditions.logicLabel')}:</span>
                      <div style={{ display: 'flex', borderRadius: 7, border: `1px solid ${BORDER_STRONG}`, overflow: 'hidden' }}>
                        <button
                          onClick={() => setLogic('AND')}
                          style={{ padding: '4px 10px', fontSize: 12, border: 'none', background: logic === 'AND' ? '#1f6feb' : 'transparent', color: logic === 'AND' ? '#fff' : MUTED, cursor: 'pointer' }}
                        >
                          {t('conditions.logicAnd')}
                        </button>
                        <button
                          onClick={() => setLogic('OR')}
                          style={{ padding: '4px 10px', fontSize: 12, border: 'none', background: logic === 'OR' ? '#1f6feb' : 'transparent', color: logic === 'OR' ? '#fff' : MUTED, cursor: 'pointer' }}
                        >
                          {t('conditions.logicOr')}
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                {/* 트리거 리스트 */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {triggers.map((tr, idx) => {
                    const isAi = tr.triggerType === 'AI_SCORE'
                    return (
                      <div
                        key={idx}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          flexWrap: 'wrap',
                          padding: 10,
                          borderRadius: 10,
                          background: isAi ? 'rgba(168, 85, 247, 0.08)' : CARD,
                          border: `1px solid ${isAi ? 'rgba(168, 85, 247, 0.3)' : BORDER_STRONG}`,
                        }}
                      >
                        {/* 트리거 유형 */}
                        <select
                          value={tr.triggerType}
                          onChange={(e) => {
                            const nextType = e.target.value as TriggerType
                            if (nextType === 'AI_SCORE') {
                              updateTrigger(idx, { triggerType: 'AI_SCORE', baseType: 'AI_SCORE', compareType: 'ABOVE', isRate: false })
                            } else if (nextType === 'PROFIT_TARGET') {
                              updateTrigger(idx, { triggerType: 'PROFIT_TARGET', baseType: 'AVG_PRICE', compareType: 'ABOVE', isRate: true })
                            } else if (nextType === 'STOP_LOSS') {
                              updateTrigger(idx, { triggerType: 'STOP_LOSS', baseType: 'AVG_PRICE', compareType: 'BELOW', isRate: true })
                            } else if (nextType === 'TRAILING_STOP') {
                              updateTrigger(idx, { triggerType: 'TRAILING_STOP', baseType: 'HIGHEST_PRICE', compareType: 'BELOW', isRate: true })
                            } else {
                              updateTrigger(idx, { triggerType: 'PRICE', baseType: 'CURRENT_PRICE', compareType: 'ABOVE', isRate: false })
                            }
                          }}
                          style={{ ...inputStyle, width: 140 }}
                        >
                          <option value="PRICE">{t('conditions.triggerPrice')}</option>
                          <option value="PROFIT_TARGET">{t('conditions.triggerProfit')}</option>
                          <option value="STOP_LOSS">{t('conditions.triggerStop')}</option>
                          <option value="TRAILING_STOP">{t('conditions.triggerTrailing')}</option>
                          <option value="AI_SCORE">{t('conditions.triggerAiScore')}</option>
                        </select>

                        {/* 비교 연산자 (≥ / ≤) */}
                        <select
                          value={tr.compareType}
                          onChange={(e) => updateTrigger(idx, { compareType: e.target.value as CompareType })}
                          style={{ ...inputStyle, width: 80 }}
                        >
                          <option value="ABOVE">≥</option>
                          <option value="BELOW">≤</option>
                        </select>

                        {/* 목표값 입력 */}
                        <input
                          type="number"
                          value={tr.targetValue}
                          onChange={(e) => updateTrigger(idx, { targetValue: e.target.value })}
                          placeholder={tr.isRate ? t('conditions.placeholderRate') : (isAi ? t('conditions.placeholderAiScore') : t('conditions.placeholderPrice'))}
                          style={{ ...inputStyle, flex: 1, minWidth: 100 }}
                        />

                        {/* 단위 표시 */}
                        <span style={{ fontSize: 13, color: MUTED, width: 30 }}>
                          {tr.isRate ? t('conditions.unitRate') : (isAi ? t('conditions.unitAiScore') : t('conditions.unitPrice'))}
                        </span>

                        {isAi && <IconSparkles size={13} style={{ color: AI_COLOR, marginLeft: -2 }} />}

                        {/* 삭제 버튼 */}
                        {triggers.length > 1 && (
                          <button
                            onClick={() => removeTrigger(idx)}
                            style={{ display: 'flex', background: 'transparent', border: 'none', color: MUTED, cursor: 'pointer', padding: 4, marginLeft: 'auto' }}
                          >
                            <IconClose size={14} />
                          </button>
                        )}
                      </div>
                    )
                  })}
                </div>

                {triggers.length < 5 && (
                  <button
                    onClick={addTrigger}
                    style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginTop: 10, fontSize: 12, color: '#58a6ff', background: 'transparent', border: 'none', cursor: 'pointer', padding: 0 }}
                  >
                    <IconPlus size={12} /> {t('conditions.addTrigger')}
                  </button>
                )}
              </div>

              {/* THEN 블록 (주문 액션) */}
              <div style={{ background: BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 16, marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                  <span style={{ fontSize: 11, fontWeight: 700, padding: '3px 9px', borderRadius: 6, background: '#238636', color: '#fff', letterSpacing: 0.5 }}>THEN</span>
                  <span style={{ fontSize: 13, fontWeight: 600, color: '#c9d1d9' }}>{t('conditions.thenTitle')}</span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
                  {/* 매수/매도 선택 */}
                  <div>
                    <label style={labelStyle}>{t('conditions.orderType')}</label>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button
                        onClick={() => setAction((p) => ({ ...p, orderType: 'BUY' }))}
                        style={{ ...toggleStyle, background: action.orderType === 'BUY' ? UP : 'transparent', color: action.orderType === 'BUY' ? '#fff' : '#c9d1d9', borderColor: action.orderType === 'BUY' ? UP : BORDER_STRONG }}
                      >
                        {t('conditions.orderBuy')}
                      </button>
                      <button
                        onClick={() => setAction((p) => ({ ...p, orderType: 'SELL' }))}
                        style={{ ...toggleStyle, background: action.orderType === 'SELL' ? DOWN : 'transparent', color: action.orderType === 'SELL' ? '#fff' : '#c9d1d9', borderColor: action.orderType === 'SELL' ? DOWN : BORDER_STRONG }}
                      >
                        {t('conditions.orderSell')}
                      </button>
                    </div>
                  </div>

                  {/* 주문 수량 */}
                  <div>
                    <label style={labelStyle}>{t('conditions.orderQuantity')}</label>
                    <input
                      type="number"
                      min="1"
                      value={action.orderQuantity}
                      onChange={(e) => setAction((p) => ({ ...p, orderQuantity: e.target.value }))}
                      style={{ ...inputStyle, width: '100%' }}
                    />
                  </div>
                </div>

                {/* 주문 방식 (시장가 / 지정가) */}
                <div style={{ display: 'grid', gridTemplateColumns: action.orderPriceType === 'LIMIT' ? '1fr 1fr' : '1fr', gap: 12, marginBottom: 16 }}>
                  <div>
                    <label style={labelStyle}>{t('conditions.orderPriceType')}</label>
                    <select
                      value={action.orderPriceType}
                      onChange={(e) => setAction((p) => ({ ...p, orderPriceType: e.target.value as 'MARKET' | 'LIMIT' }))}
                      style={{ ...inputStyle, width: '100%' }}
                    >
                      <option value="MARKET">{t('conditions.priceTypeMarket')}</option>
                      <option value="LIMIT">{t('conditions.priceTypeLimit')}</option>
                    </select>
                  </div>
                  {action.orderPriceType === 'LIMIT' && (
                    <div>
                      <label style={labelStyle}>{t('conditions.limitPrice')}</label>
                      <input
                        type="number"
                        value={action.limitPrice}
                        onChange={(e) => setAction((p) => ({ ...p, limitPrice: e.target.value }))}
                        placeholder={t('conditions.placeholderPrice')}
                        style={{ ...inputStyle, width: '100%' }}
                      />
                    </div>
                  )}
                </div>

                {/* ── 실행 방식 (완전 자동 vs 반자동 제안) ── */}
                <div style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 14 }}>
                  <label style={labelStyle}>{t('conditions.executionModeLabel')}</label>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                    <button
                      type="button"
                      onClick={() => setExecutionMode('AUTO')}
                      style={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: 8,
                        padding: '10px 12px',
                        borderRadius: 10,
                        border: `1px solid ${executionMode === 'AUTO' ? '#238636' : BORDER_STRONG}`,
                        background: executionMode === 'AUTO' ? 'rgba(35, 134, 54, 0.15)' : 'transparent',
                        color: executionMode === 'AUTO' ? '#3fb950' : MUTED,
                        textAlign: 'left',
                        cursor: 'pointer',
                      }}
                    >
                      <IconZap size={14} style={{ marginTop: 2, flexShrink: 0 }} />
                      <span>
                        <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 2 }}>{t('conditions.modeAutoTitle')}</div>
                        <div style={{ fontSize: 11, opacity: 0.8 }}>{t('conditions.modeAutoDesc')}</div>
                      </span>
                    </button>

                    <button
                      type="button"
                      onClick={() => setExecutionMode('MANUAL')}
                      style={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: 8,
                        padding: '10px 12px',
                        borderRadius: 10,
                        border: `1px solid ${executionMode === 'MANUAL' ? AI_COLOR : BORDER_STRONG}`,
                        background: executionMode === 'MANUAL' ? 'rgba(168, 85, 247, 0.15)' : 'transparent',
                        color: executionMode === 'MANUAL' ? '#d8b4fe' : MUTED,
                        textAlign: 'left',
                        cursor: 'pointer',
                      }}
                    >
                      <IconBell size={14} style={{ marginTop: 2, flexShrink: 0 }} />
                      <span>
                        <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 2 }}>{t('conditions.modeManualTitle')}</div>
                        <div style={{ fontSize: 11, opacity: 0.8 }}>{t('conditions.modeManualDesc')}</div>
                      </span>
                    </button>
                  </div>
                </div>

                {/* ── 반복 감시 (isPersistent) ── */}
                <div style={{ borderTop: `1px solid ${BORDER}`, marginTop: 14, paddingTop: 14 }}>
                  <button
                    type="button"
                    onClick={() => setIsPersistent((p) => !p)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      width: '100%',
                      padding: '10px 12px',
                      borderRadius: 10,
                      border: `1px solid ${isPersistent ? '#1f6feb' : BORDER_STRONG}`,
                      background: isPersistent ? 'rgba(31, 111, 235, 0.15)' : 'transparent',
                      cursor: 'pointer',
                      textAlign: 'left',
                    }}
                  >
                    <span style={{
                      position: 'relative', width: 36, height: 20, borderRadius: 10, flexShrink: 0,
                      background: isPersistent ? '#1f6feb' : BORDER_STRONG, transition: 'background 0.2s',
                    }}>
                      <span style={{
                        position: 'absolute', top: 2, left: isPersistent ? 18 : 2, width: 16, height: 16, borderRadius: '50%',
                        background: '#fff', transition: 'left 0.2s',
                      }} />
                    </span>
                    <IconRepeat size={14} style={{ color: isPersistent ? '#58a6ff' : MUTED, flexShrink: 0 }} />
                    <span>
                      <div style={{ fontSize: 13, fontWeight: 700, color: isPersistent ? '#58a6ff' : '#c9d1d9' }}>
                        {t('conditions.isPersistentLabel')}
                      </div>
                      <div style={{ fontSize: 11, color: MUTED, opacity: 0.9 }}>{t('conditions.isPersistentDesc')}</div>
                    </span>
                  </button>
                </div>
              </div>

              {message && (
                <p style={{ fontSize: 13, color: '#58a6ff', margin: '0 0 12px', textAlign: 'center' }}>
                  {message}
                </p>
              )}

              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  onClick={submit}
                  disabled={createMutation.isPending || updateMutation.isPending}
                  style={{
                    flex: 1,
                    padding: '12px',
                    fontSize: 14,
                    fontWeight: 700,
                    cursor: 'pointer',
                    borderRadius: 10,
                    border: 'none',
                    background: 'linear-gradient(135deg, #3b82f6, #06b6d4)',
                    color: '#fff',
                    opacity: createMutation.isPending || updateMutation.isPending ? 0.6 : 1,
                  }}
                >
                  {editingId != null ? t('conditions.saveEdit') : t('conditions.startWatch')}
                </button>
                {editingId != null && (
                  <button
                    onClick={cancelEdit}
                    style={{ padding: '12px 16px', fontSize: 13, cursor: 'pointer', borderRadius: 10, border: `1px solid ${BORDER_STRONG}`, background: 'transparent', color: MUTED }}
                  >
                    {t('conditions.cancelEdit')}
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* ── 우측: 감시 현황판 ── */}
          <div className="cond-sidebar">
            <div style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 14, padding: 20 }}>
              <p style={{ fontSize: 12, color: MUTED, margin: '0 0 12px', textTransform: 'uppercase', letterSpacing: 1 }}>
                {t('conditions.watchTitle')} {conditions.length > 0 ? `(${conditions.length})` : ''}
              </p>
              {conditions.length === 0 ? (
                <p style={{ color: MUTED, fontSize: 13 }}>{t('conditions.noConditionHint')}</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {conditions.map((c) => {
                    const stock = stocks.find((s) => s.stockCode === c.stockCode)
                    const stockLabel = stock ? stock.stockName : c.stockCode
                    const trailingTrigger = c.triggers.find((tr) => tr.triggerType === 'TRAILING_STOP')
                    const hasAiTrigger = c.triggers.some((tr) => tr.triggerType === 'AI_SCORE')
                    const isManualMode = c.executionMode === 'MANUAL'

                    return (
                      <div
                        key={c.conditionId}
                        className="cond-item"
                        style={{
                          border: `1px solid ${hasAiTrigger ? 'rgba(168,85,247,0.3)' : BORDER}`,
                          borderRadius: 12,
                          padding: '12px 14px',
                          background: BG,
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                            <span style={{ fontWeight: 600, fontSize: 14 }}>{stockLabel}</span>
                            {hasAiTrigger && (
                              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 10, padding: '2px 6px', borderRadius: 5, background: 'rgba(168,85,247,0.15)', color: '#d8b4fe', border: '1px solid rgba(168,85,247,0.3)', fontWeight: 600 }}>
                                <IconSparkles size={9} />{t('conditions.aiWatchBadge')}
                              </span>
                            )}
                            <span
                              style={{
                                display: 'inline-flex', alignItems: 'center', gap: 3,
                                fontSize: 10,
                                padding: '2px 6px',
                                borderRadius: 5,
                                background: isManualMode ? 'rgba(168,85,247,0.1)' : 'rgba(35,134,54,0.1)',
                                color: isManualMode ? '#d8b4fe' : '#3fb950',
                                border: `1px solid ${isManualMode ? 'rgba(168,85,247,0.3)' : 'rgba(35,134,54,0.3)'}`,
                                fontWeight: 600,
                              }}
                            >
                              {isManualMode ? <IconBell size={9} /> : <IconZap size={9} />}
                              {isManualMode ? t('conditions.badgeManual') : t('conditions.badgeAuto')}
                            </span>
                            {c.isPersistent && (
                              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 10, padding: '2px 6px', borderRadius: 5, background: 'rgba(31,111,235,0.1)', color: '#58a6ff', border: '1px solid rgba(31,111,235,0.3)', fontWeight: 600 }}>
                                <IconRepeat size={9} />{t('conditions.isPersistentBadge')}
                              </span>
                            )}
                          </div>
                          <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexShrink: 0 }}>
                            {/* ON/OFF 스위치 */}
                            <button
                              onClick={() => toggleMutation.mutate({ id: c.conditionId, active: !c.isActive })}
                              style={{
                                position: 'relative', width: 40, height: 22, borderRadius: 11, cursor: 'pointer', border: 'none',
                                background: c.isActive ? '#238636' : BORDER_STRONG, transition: 'background 0.2s',
                              }}
                              title={c.isActive ? t('conditions.watching') : t('conditions.stopped')}
                            >
                              <span style={{
                                position: 'absolute', top: 3, left: c.isActive ? 21 : 3, width: 16, height: 16, borderRadius: '50%',
                                background: '#fff', transition: 'left 0.2s',
                              }} />
                            </button>
                            <button onClick={() => startEdit(c.conditionId, c)} style={{ display: 'flex', color: '#58a6ff', background: 'transparent', border: 'none', cursor: 'pointer', padding: 4 }}>
                              <IconEdit size={13} />
                            </button>
                            <button onClick={() => deleteMutation.mutate(c.conditionId)} style={{ display: 'flex', color: UP, background: 'transparent', border: 'none', cursor: 'pointer', padding: 4 }}>
                              <IconTrash size={13} />
                            </button>
                          </div>
                        </div>

                        {/* 트리거 요약 */}
                        <div style={{ fontSize: 12, color: MUTED, display: 'flex', flexDirection: 'column', gap: 4 }}>
                          {c.triggers.map((tr, i) => {
                            const isAi = tr.triggerType === 'AI_SCORE'
                            return (
                              <span key={tr.triggerId || i} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                {i > 0 && <span style={{ color: '#58a6ff', marginRight: 4, fontWeight: 700 }}>{c.conditionLogic}</span>}
                                {isAi && <IconSparkles size={10} style={{ color: AI_COLOR, flexShrink: 0 }} />}
                                <span style={{ color: isAi ? '#d8b4fe' : '#c9d1d9', fontWeight: isAi ? 600 : 400 }}>
                                  {t(TRIGGER_TYPE_KEYS[tr.triggerType])} {tr.compareType === 'ABOVE' ? '≥' : '≤'} {tr.targetValue}{tr.isRate ? '%' : (isAi ? '점' : '원')}
                                </span>
                              </span>
                            )
                          })}
                        </div>

                        {/* 트레일링 스탑 시각화 */}
                        {trailingTrigger && trailingTrigger.trailingHighest != null && (
                          <div style={{ marginTop: 8, fontSize: 11, color: MUTED }}>
                            <span>{t('conditions.currentHighest')}: ₩{trailingTrigger.trailingHighest.toLocaleString()}</span>
                            <span style={{ marginLeft: 8, color: DOWN }}>
                              {t('conditions.sellTriggerPrice')}: ₩{((trailingTrigger.trailingHighest) * (1 + trailingTrigger.targetValue / 100)).toLocaleString()}
                            </span>
                          </div>
                        )}

                        <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', gap: 8, paddingTop: 6, borderTop: `1px solid ${CARD}` }}>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 11, color: c.isActive ? '#3fb950' : MUTED, fontWeight: 600 }}>
                            <span style={{ width: 6, height: 6, borderRadius: '50%', background: c.isActive ? '#3fb950' : MUTED, display: 'inline-block' }} />
                            {c.isActive ? t('conditions.watching') : t('conditions.stopped')}
                          </span>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2, fontSize: 11, color: c.orderType === 'BUY' ? UP : DOWN, fontWeight: 700 }}>
                            {c.orderType === 'BUY' ? <IconArrowUp size={10} /> : <IconArrowDown size={10} />}
                            {t(c.orderType === 'BUY' ? 'conditions.orderBuy' : 'conditions.orderSell')}
                          </span>
                          <span style={{ fontSize: 11, color: '#c9d1d9' }}>{c.orderQuantity}{t('trading.shares')}</span>
                          <span style={{ fontSize: 10, color: '#6e7681', marginLeft: 'auto' }}>
                            {c.orderPriceType === 'LIMIT' && c.limitPrice ? `${t('conditions.priceTypeLimit')} ₩${c.limitPrice.toLocaleString()}` : t('conditions.priceTypeMarket')}
                          </span>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* ── 매매 이력 (api.md §3.3 GET /assets/histories) ── */}
        <div style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 14, padding: 20, marginTop: 24 }}>
          <p style={{ fontSize: 12, color: MUTED, margin: '0 0 12px', textTransform: 'uppercase', letterSpacing: 1 }}>
            {t('conditions.historyTitle')}
          </p>
          {histories.length === 0 ? (
            <p style={{ color: MUTED, fontSize: 13 }}>{t('conditions.noHistoryHint')}</p>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                <thead>
                  <tr style={{ color: MUTED, textAlign: 'left', borderBottom: `1px solid ${BORDER}` }}>
                    <th style={historyThStyle}>{t('conditions.historyStock')}</th>
                    <th style={historyThStyle}>{t('conditions.historyType')}</th>
                    <th style={historyThStyle}>{t('conditions.historyStatus')}</th>
                    <th style={historyThStyle}>{t('conditions.historyPrice')}</th>
                    <th style={historyThStyle}>{t('conditions.historyQuantity')}</th>
                    <th style={historyThStyle}>{t('conditions.historyTotal')}</th>
                    <th style={historyThStyle}>{t('conditions.historyTime')}</th>
                  </tr>
                </thead>
                <tbody>
                  {histories.map((h) => {
                    const stock = stocks.find((s) => s.stockCode === h.stockCode)
                    const statusColor = h.status === 'FILLED' ? '#3fb950' : h.status === 'FAILED' ? UP : MUTED
                    return (
                      <tr key={h.historyId} style={{ borderBottom: `1px solid ${BORDER}` }}>
                        <td style={historyTdStyle}>{stock ? stock.stockName : h.stockCode}</td>
                        <td style={{ ...historyTdStyle, color: h.orderType === 'BUY' ? UP : DOWN, fontWeight: 600 }}>
                          {t(h.orderType === 'BUY' ? 'conditions.orderBuy' : 'conditions.orderSell')}
                        </td>
                        <td style={{ ...historyTdStyle, color: statusColor, fontWeight: 600 }}>
                          {h.status === 'FAILED' && h.failureReason ? h.failureReason : t(`conditions.historyStatus${h.status}`)}
                        </td>
                        <td style={historyTdStyle}>{h.executionPrice != null ? `₩${h.executionPrice.toLocaleString()}` : '-'}</td>
                        <td style={historyTdStyle}>{h.executionQuantity ?? '-'}</td>
                        <td style={historyTdStyle}>{h.totalAmount != null ? `₩${h.totalAmount.toLocaleString()}` : '-'}</td>
                        <td style={{ ...historyTdStyle, color: MUTED }}>{new Date(h.requestedAt).toLocaleString()}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

const labelStyle: React.CSSProperties = { display: 'block', fontSize: 12, color: MUTED, margin: '0 0 6px' }
const inputStyle: React.CSSProperties = {
  background: BG, border: `1px solid ${BORDER_STRONG}`, borderRadius: 7, padding: '8px 10px', color: TEXT, fontSize: 13, outline: 'none',
}
const selectStyle: React.CSSProperties = { ...inputStyle, width: '100%', marginBottom: 16 }
const toggleStyle: React.CSSProperties = { flex: 1, padding: '8px', fontSize: 13, cursor: 'pointer', borderRadius: 9, border: `1px solid ${BORDER_STRONG}`, background: 'transparent', color: '#c9d1d9' }
const presetButtonStyle: React.CSSProperties = {
  padding: '6px 12px', borderRadius: 8, cursor: 'pointer', fontSize: 12, fontWeight: 500,
  border: `1px solid ${BORDER_STRONG}`, background: 'rgba(255, 255, 255, 0.03)', color: '#c9d1d9',
}
const aiPresetButtonStyle: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 5,
  padding: '6px 12px', borderRadius: 8, cursor: 'pointer', fontSize: 12, fontWeight: 600,
  border: '1px solid rgba(168, 85, 247, 0.4)', background: 'rgba(168, 85, 247, 0.08)', color: '#d8b4fe',
}
const historyThStyle: React.CSSProperties = { padding: '6px 10px', fontWeight: 600 }
const historyTdStyle: React.CSSProperties = { padding: '8px 10px' }
